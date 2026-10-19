package ar.edu.uade.medlink.admin;

import ar.edu.uade.medlink.admin.AdminDtos.*;
import ar.edu.uade.medlink.common.PageDTO;
import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.auth.AuthDtos.CuentaPerfilDTO;
import ar.edu.uade.medlink.auth.AuthDtos.UsuarioPerfilDTO;
import ar.edu.uade.medlink.auth.AuthService;
import ar.edu.uade.medlink.common.AuditService;
import ar.edu.uade.medlink.common.PageRequests;
import static ar.edu.uade.medlink.common.RequestValidation.requiredReason;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdministrationService {
    private final JdbcTemplate db;
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final AuditService audit;
    private final ReservationClosureService closure;
    public AdministrationService(JdbcTemplate db, AccountRepository accounts, PasswordEncoder passwords,
                                 Clock clock, AuditService audit, ReservationClosureService closure) {
        this.db=db;this.accounts=accounts;this.passwords=passwords;
        this.clock=clock;this.audit=audit;this.closure=closure;
    }
    private static final String OPERATORS="SELECT o.id_operador,o.id_cuenta,o.nombre,o.apellido,c.correo,"+
        "c.estado::text AS estado,c.token_version,o.id_farmacia,f.nombre AS farmacia_nombre FROM operador o " +
        "JOIN cuenta c ON c.id_cuenta=o.id_cuenta JOIN farmacia f ON f.id_farmacia=o.id_farmacia ";
    private static final org.springframework.jdbc.core.RowMapper<OperadorDTO> OPERATOR=(r,n)->
        new OperadorDTO(r.getLong("id_operador"),r.getLong("id_cuenta"),r.getString("nombre"),
            r.getString("apellido"),r.getString("correo"),r.getString("estado"),
            r.getLong("id_farmacia"),r.getString("farmacia_nombre"));

    @Transactional(readOnly=true)
    public OperadorDTO operator(long id) {
        return db.query(OPERATORS+" WHERE o.id_operador=?",OPERATOR,id).stream().findFirst().orElseThrow(ApiException::notFound);
    }
    @Transactional
    public OperadorDTO createOperator(OperadorCreateRequest req, CurrentAccount actor, HttpServletRequest request) {
        String nombre=AuthService.name(req.nombre()), apellido=AuthService.name(req.apellido());
        String email=AuthService.email(req.correo());
        AuthService.initialPassword(req.contrasenaInicial());
        // Guard farmacia ensures concurrent changes of operational status serialize.
        if (db.queryForList("SELECT id_farmacia FROM farmacia WHERE id_farmacia=? FOR UPDATE",
            Long.class,req.farmaciaId()).isEmpty()) throw ApiException.notFound();
        if (accounts.byEmail(email).isPresent())
            throw ApiException.conflict("EMAIL_ALREADY_EXISTS","Ya existe una cuenta con ese correo.");
        try {
            long id=accounts.newAccount(email,passwords.encode(req.contrasenaInicial()),"OPERADOR",clock.instant());
            long operatorId=db.queryForObject("INSERT INTO operador (id_cuenta,id_farmacia,nombre,apellido) " +
                "VALUES (?,?,?,?) RETURNING id_operador",Long.class,id,req.farmaciaId(),nombre,apellido);
            audit.record(actor.id(),actor.rol(),"CREAR_OPERADOR","OPERADOR",operatorId,null,null,
                null,Map.of("cuentaId",id,"farmaciaId",req.farmaciaId(),"nombre",nombre,"apellido",apellido),request);
            return operator(operatorId);
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict("EMAIL_ALREADY_EXISTS","Ya existe una cuenta con ese correo.");
        }
    }
    @Transactional
    public OperadorDTO updateOperator(long id, OperadorUpdateRequest req, CurrentAccount actor,
                                      HttpServletRequest request) {
        String name=AuthService.name(req.nombre()),surname=AuthService.name(req.apellido());
        OperadorDTO before=operator(id);
        // Locked account makes profile updates atomic with account deactivation.
        accounts.byIdLocked(before.cuentaId());
        db.queryForList("SELECT id_operador FROM operador WHERE id_operador=? FOR UPDATE",Long.class,id);
        before=operator(id);
        db.update("UPDATE operador SET nombre=?,apellido=? WHERE id_operador=?",name,surname,id);
        audit.record(actor.id(),actor.rol(),"EDITAR_OPERADOR","OPERADOR",id,null,null,
            Map.of("nombre",before.nombre(),"apellido",before.apellido()),
            Map.of("nombre",name,"apellido",surname),request);
        return operator(id);
    }
    @Transactional
    public CuentaPerfilDTO changeState(long accountId, boolean enabled, String reason, CurrentAccount actor,
                                        HttpServletRequest request) {
        String motivo=requiredReason(reason);
        var account=accounts.byIdLocked(accountId);
        if (account.rol().equals("ADMINISTRADOR")) throw ApiException.forbidden();
        String target=enabled?"ACTIVA":"DESHABILITADA";
        if (account.estado().equals(target))
            throw ApiException.conflict("INVALID_STATE_TRANSITION","La cuenta ya se encuentra en ese estado.");
        Long userId=null;
        if (account.rol().equals("USUARIO")) {
            userId=accounts.userProfile(accountId).usuarioId();
            // Account -> Usuario -> farmacia/medication/presentation/stock/... (RN-40 / 10.2).
            db.queryForList("SELECT id_usuario FROM usuario WHERE id_usuario=? FOR UPDATE",Long.class,userId);
            if (!enabled) closure.terminateUserReservations(userId,clock.instant(),request);
        } else {
            long pharmacyId=db.queryForObject("SELECT id_farmacia FROM operador WHERE id_cuenta=?",Long.class,accountId);
            String pharmacyStatus=db.query("SELECT estado::text FROM farmacia WHERE id_farmacia=? FOR UPDATE",
                (r,n)->r.getString(1),pharmacyId).stream().findFirst().orElseThrow(ApiException::notFound);
            if (!enabled && "ACTIVA".equals(pharmacyStatus)) {
                int count=db.queryForObject("SELECT COUNT(*) FROM operador o JOIN cuenta c ON c.id_cuenta=o.id_cuenta " +
                    "WHERE o.id_farmacia=? AND c.estado='ACTIVA'",Integer.class,pharmacyId);
                if (count<=1) throw ApiException.conflict("LAST_OPERATIONAL_OPERATOR",
                    "No es posible deshabilitar al último operador operativo de una farmacia activa.");
            }
        }
        accounts.changeState(accountId,target,!enabled);
        audit.record(actor.id(),actor.rol(),enabled?"REACTIVAR_CUENTA":"DESHABILITAR_CUENTA",
            "CUENTA",accountId,userId,motivo,
            Map.of("estado",account.estado(),"tokenVersion",account.tokenVersion()),
            Map.of("estado",target,"tokenVersion",account.tokenVersion()+(enabled?0:1)),request);
        return accounts.perfil(accountId);
    }
    @Transactional(readOnly=true)
    public PageDTO<OperadorDTO> operators(Long pharmacy, String state, String email,
        int page, int size, List<String> sort) {
        PageRequests.validate(page,size);
        StringBuilder where=new StringBuilder(" WHERE 1=1");
        List<Object> values=new ArrayList<>();
        if (pharmacy!=null) { where.append(" AND o.id_farmacia=?"); values.add(pharmacy); }
        if (state!=null&&!state.isBlank()) {
            checkState(state); where.append(" AND c.estado=?::estado_cuenta");values.add(state);
        }
        if (email!=null&&!email.isBlank()) {
            where.append(" AND lower(c.correo) LIKE ? ESCAPE '!'");values.add(emailPattern(email));
        }
        long count=db.queryForObject("SELECT COUNT(*) FROM operador o JOIN cuenta c ON c.id_cuenta=o.id_cuenta"+where,
            Long.class,values.toArray());
        String order=sortClause(sort,Map.of("apellido","o.apellido","nombre","o.nombre","correo","c.correo","id","o.id_operador"),
            "o.apellido ASC,o.nombre ASC,o.id_operador ASC");
        List<Object> args=new ArrayList<>(values); args.add(size);args.add(page*size);
        List<OperadorDTO> data=db.query(OPERATORS+where+" ORDER BY "+order+" LIMIT ? OFFSET ?",OPERATOR,args.toArray());
        return PageDTO.from(data,page,size,count);
    }
    @Transactional(readOnly=true)
    public PageDTO<UsuarioPerfilDTO> users(String email, String state, int page, int size, List<String> sort) {
        PageRequests.validate(page,size);
        StringBuilder where=new StringBuilder(" WHERE 1=1");List<Object> args=new ArrayList<>();
        if (email!=null&&!email.isBlank()) {
            where.append(" AND lower(c.correo) LIKE ? ESCAPE '!'");args.add(emailPattern(email));
        }
        if (state!=null&&!state.isBlank()) {
            checkState(state);where.append(" AND c.estado=?::estado_cuenta");args.add(state);
        }
        String from=" FROM usuario u JOIN cuenta c ON c.id_cuenta=u.id_cuenta";
        long total=db.queryForObject("SELECT count(*)"+from+where,Long.class,args.toArray());
        String order=sortClause(sort,Map.of("apellido","u.apellido","nombre","u.nombre","correo","c.correo","id","u.id_usuario"),
            "u.apellido ASC,u.nombre ASC,u.id_usuario ASC");
        List<Object> query=new ArrayList<>(args);query.add(size);query.add(page*size);
        var data=db.query("SELECT u.id_usuario,u.id_cuenta,u.nombre,u.apellido,c.correo,c.estado::text AS estado"+from+
            where+" ORDER BY "+order+" LIMIT ? OFFSET ?",(r,n)->new UsuarioPerfilDTO(r.getLong(1),r.getLong(2),
            r.getString(3),r.getString(4),r.getString(5),r.getString(6),"USUARIO"),query.toArray());
        return PageDTO.from(data,page,size,total);
    }
    private static void checkState(String status) {
        if (!status.equals("ACTIVA")&&!status.equals("DESHABILITADA")) throw ApiException.validation("Estado inválido.");
    }
    private static String emailPattern(String email) {
        return "%" + email.strip().toLowerCase(java.util.Locale.ROOT)
            .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
    static String sortClause(List<String> specifications, Map<String,String> columns, String defaultSort) {
        if (specifications==null||specifications.isEmpty()) return defaultSort;
        List<String> validated=new ArrayList<>();
        for (String s:specifications) {
            String[] parts=s.split(",",-1);
            if (parts.length!=2||!columns.containsKey(parts[0])||
                !(parts[1].equalsIgnoreCase("asc")||parts[1].equalsIgnoreCase("desc")))
                throw ApiException.validation("Ordenamiento inválido.");
            validated.add(columns.get(parts[0])+" "+parts[1].toUpperCase(java.util.Locale.ROOT));
        }
        if (specifications.stream().noneMatch(spec -> spec.startsWith("id,")))
            validated.add(columns.get("id")+" ASC");
        return String.join(",",validated);
    }
}
