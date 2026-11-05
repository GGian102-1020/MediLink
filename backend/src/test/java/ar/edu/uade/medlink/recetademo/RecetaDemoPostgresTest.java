package ar.edu.uade.medlink.recetademo;

import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.common.AuditService;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.escaneo.PrescriptionImageExtractor;
import ar.edu.uade.medlink.prescripcion.service.PrescripcionService;
import ar.edu.uade.medlink.reserva.dto.CrearReservaRequest;
import ar.edu.uade.medlink.reserva.service.ReservaService;
import ar.edu.uade.medlink.security.CurrentAccount;
import ar.edu.uade.medlink.security.JwtTokens;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import java.sql.Timestamp;
import java.time.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import static ar.edu.uade.medlink.escaneo.PrescriptionImageExtractor.*;
import static ar.edu.uade.medlink.recetademo.RecetaDemoDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"medlink.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "spring.flyway.baseline-on-migrate=false","medlink.reservas.scheduler.enabled=false",
    "medlink.recetas-demo.scan.enabled=false","medlink.recetas-demo.scan.max-bytes=1048576",
    "medlink.recetas-demo.pdf-alterado.enabled=true"})
@AutoConfigureMockMvc
@EnabledIf("postgresDisponible")
class RecetaDemoPostgresTest {
    static PostgreSQLContainer container;
    static boolean postgresDisponible() {
        return System.getenv("MEDLINK_TEST_DB_URL")!=null || DockerClientFactory.instance().isDockerAvailable();
    }
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry registry) {
        String url=System.getenv("MEDLINK_TEST_DB_URL");
        if (url==null) {
            container=new PostgreSQLContainer("postgres:16-alpine");
            container.start();
            registry.add("spring.datasource.url",container::getJdbcUrl);
            registry.add("spring.datasource.username",container::getUsername);
            registry.add("spring.datasource.password",container::getPassword);
        } else {
            registry.add("spring.datasource.url",()->url);
            registry.add("spring.datasource.username",()->System.getenv().getOrDefault("MEDLINK_TEST_DB_USER","postgres"));
            registry.add("spring.datasource.password",()->System.getenv().getOrDefault("MEDLINK_TEST_DB_PASSWORD",""));
        }
    }
    @AfterAll static void cerrar() { if (container!=null) container.stop(); }

    @Autowired JdbcTemplate db;
    @Autowired AccountRepository cuentas;
    @Autowired JwtTokens tokens;
    @Autowired Clock clock;
    @Autowired RecetaDemoService recetas;
    @Autowired RecetaDemoRepository repositorio;
    @Autowired PrescripcionService prescripciones;
    @Autowired ReservaService reservas;
    @Autowired PlatformTransactionManager tx;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoSpyBean AuditService audit;
    @MockitoBean PrescriptionImageExtractor extractor;
    long farmacia,presentacion,stock,usuario,otroUsuario;
    CurrentAccount admin,paciente,otro,operador;

    @BeforeEach void datos() {
        new TransactionTemplate(tx).executeWithoutResult(status -> {
            String sufijo=UUID.randomUUID().toString();
            farmacia=id("""
                INSERT INTO farmacia(nombre,direccion,latitud,longitud) VALUES (?,'Calle 123',-34.6037,-58.3816)
                RETURNING id_farmacia
                ""","Farmacia "+sufijo);
            long medicamento=id("INSERT INTO medicamento(nombre) VALUES (?) RETURNING id_medicamento","Levotiroxina "+sufijo);
            presentacion=id("""
                INSERT INTO presentacion_medicamento(id_medicamento,concentracion_texto,forma_farmaceutica,
                    cantidad_envase,unidad_expendio,condicion_dispensa)
                VALUES (?,'100 mcg','Comprimidos',50,'Caja','BAJO_RECETA') RETURNING id_presentacion
                """,medicamento);
            stock=id("""
                INSERT INTO stock(id_farmacia,id_presentacion,cantidad_disponible,fecha_hora_actualizacion)
                VALUES (?,?,10,?) RETURNING id_stock
                """,farmacia,presentacion,Timestamp.from(clock.instant()));
            admin=cuenta("ADMINISTRADOR",sufijo);
            db.update("INSERT INTO administrador(id_cuenta,nombre,apellido) VALUES (?,'Admin','Demo')",admin.id());
            operador=cuenta("OPERADOR",sufijo);
            db.update("INSERT INTO operador(id_cuenta,id_farmacia,nombre,apellido) VALUES (?,?,'Operador','Demo')",
                operador.id(),farmacia);
            paciente=cuenta("USUARIO",sufijo);
            usuario=cuentas.newUser(paciente.id(),"Paciente","Demo");
            otro=cuenta("USUARIO","otro-"+sufijo);
            otroUsuario=cuentas.newUser(otro.id(),"Visitante","Demo");
        });
    }

    private CurrentAccount cuenta(String rol,String sufijo) {
        long id=cuentas.newAccount(rol+"-"+sufijo+"@demo.test","hash",rol,clock.instant());
        return new CurrentAccount(id,rol,"test",0);
    }
    private long id(String sql,Object... args) { return db.queryForObject(sql,Long.class,args); }
    private int numero(String sql,Object... args) { return db.queryForObject(sql,Integer.class,args); }
    private String bearer(CurrentAccount actor) {
        return "Bearer "+tokens.create(cuentas.byId(actor.id()).orElseThrow());
    }
    private LocalDate hoy() { return LocalDate.now(clock); }
    private Lote lote(int cantidad) {
        return recetas.generar(new GenerarLote(presentacion,cantidad,hoy().plusDays(3)),admin,null);
    }
    private Receta receta() { return lote(1).recetas().getFirst(); }
    private Vinculacion reclamar(Receta receta,CurrentAccount actor) {
        return recetas.vincular(receta.codigoReceta(),actor,null);
    }
    private ApiException error(Runnable operacion) { return assertThrows(ApiException.class,operacion::run); }

    @Test void loteDeTreintaConCodigosUnicosSinTitularYAuditados() throws Exception {
        var response=mvc.perform(post("/api/admin/recetas-demo/lotes").header("Authorization",bearer(admin))
            .contentType("application/json").content(json.writeValueAsString(new GenerarLote(presentacion,30,hoy().plusDays(2)))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var lote=json.readValue(response,Lote.class);
        assertEquals(30,lote.recetas().size());
        assertEquals(30,lote.recetas().stream().map(Receta::codigoReceta).distinct().count());
        assertTrue(lote.recetas().stream().allMatch(r->r.codigoReceta().matches("MLD-[0-9A-F]{32}")));
        var secundarios=db.queryForList("SELECT codigo_verificacion FROM receta_demo WHERE lote=?",String.class,lote.lote());
        assertEquals(30,secundarios.stream().distinct().count());
        assertTrue(secundarios.stream().allMatch(codigo -> codigo.matches("MED-[0-9A-HJKMNP-TV-Z]{4}(-[0-9A-HJKMNP-TV-Z]{4}){2}")));
        assertThrows(DuplicateKeyException.class,()->db.update("""
            UPDATE receta_demo SET codigo_verificacion=? WHERE lote=? AND codigo_verificacion<>?
            """,secundarios.getFirst(),lote.lote(),secundarios.getFirst()));
        assertEquals(30,numero("""
            SELECT count(*) FROM receta_demo WHERE lote=? AND estado='DISPONIBLE'
                AND id_usuario IS NULL AND id_prescripcion IS NULL
            """,lote.lote()));
        assertEquals(30,numero("""
            SELECT count(*) FROM auditoria a JOIN receta_demo d ON d.id_receta_demo=a.entidad_id
            WHERE d.lote=? AND a.accion='GENERAR_RECETA_DEMO' AND a.actor_cuenta_id=?
              AND NOT jsonb_exists(a.resumen_posterior,'codigoReceta')
              AND NOT jsonb_exists(a.resumen_posterior,'codigoVerificacion')
            """,
            lote.lote(),admin.id()));
        var pdf=mvc.perform(get("/api/admin/recetas-demo/lotes/{lote}/pdf",lote.lote())
            .header("Authorization",bearer(admin))).andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf")).andReturn().getResponse().getContentAsByteArray();
        try (var doc=org.apache.pdfbox.Loader.loadPDF(pdf)) { assertEquals(30,doc.getNumberOfPages()); }
        mvc.perform(get("/api/admin/recetas-demo/{id}/pdf",lote.recetas().getFirst().id())
            .header("Authorization",bearer(admin))).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store"));
    }

    @Test void vinculaAlUsuarioAutenticadoYRepiteSinDuplicar() throws Exception {
        var r=receta();
        String cuerpo=json.writeValueAsString(new Vincular(r.codigoReceta()));
        String conTitularAjeno=json.writeValueAsString(java.util.Map.of("codigoReceta",r.codigoReceta(),
            "usuarioId",otroUsuario));
        mvc.perform(post("/api/me/recetas-demo/vinculacion").header("Authorization",bearer(paciente))
            .contentType("application/json").content(conTitularAjeno))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nueva").value(true))
            .andExpect(jsonPath("$.codigo").doesNotExist())
            .andExpect(jsonPath("$.prescripcion.usuarioId").value(usuario));
        mvc.perform(post("/api/me/recetas-demo/vinculacion").header("Authorization",bearer(paciente))
            .contentType("application/json").content(cuerpo))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nueva").value(false));
        assertEquals(1,numero("SELECT count(*) FROM prescripcion WHERE codigo_externo=?",r.codigoReceta()));
        assertEquals(1,numero("SELECT count(*) FROM auditoria WHERE accion='VINCULAR_RECETA_DEMO' AND entidad_id=? AND usuario_relacionado_id=?",r.id(),usuario));
        assertEquals("RESOURCE_NOT_FOUND",error(()->reclamar(r,otro)).codigo());
    }

    @Test void otroTitularYCodigoInexistenteTienenElMismoErrorYDetallePrivado() throws Exception {
        var r=receta();
        long id=reclamar(r,paciente).prescripcion().id();
        var ajena=error(()->reclamar(r,otro));
        var inexistente=error(()->recetas.vincular(RecetaDemoService.nuevoCodigo(),otro,null));
        assertEquals(inexistente.codigo(),ajena.codigo());
        assertEquals(inexistente.getMessage(),ajena.getMessage());
        mvc.perform(get("/api/me/prescripciones").header("Authorization",bearer(otro)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/api/me/prescripciones/{id}",id).header("Authorization",bearer(otro)))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/me/prescripciones/{id}",id).header("Authorization",bearer(paciente)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.codigoExterno").value(r.codigoReceta()));
    }

    @Test void dosCuentasSoloPuedenAdjudicarUnaPrescripcion() throws Exception {
        var r=receta();
        var resultados=concurrentes(()->resultado(()->reclamar(r,paciente)),()->resultado(()->reclamar(r,otro)));
        assertEquals(1,resultados.stream().filter("OK"::equals).count());
        assertEquals(1,resultados.stream().filter("RESOURCE_NOT_FOUND"::equals).count());
        assertEquals(1,numero("SELECT count(*) FROM prescripcion WHERE codigo_externo=?",r.codigoReceta()));
    }

    @Test void recetaVencidaOIdentificadorInvalidoNoCreanPrescripcion() {
        var r=receta();
        db.update("UPDATE receta_demo SET fecha_emision=?,fecha_vencimiento=? WHERE id_receta_demo=?",
            hoy().minusDays(2),hoy().minusDays(1),r.id());
        assertEquals("RECETA_DEMO_NO_VIGENTE",error(()->reclamar(r,paciente)).codigo());
        assertEquals("RESOURCE_NOT_FOUND",error(()->recetas.vincular("no es un código",paciente,null)).codigo());
        sinVinculacion(r);
    }

    @Test void rechazoDeAnonimoOperadorYCuentaDeshabilitada() throws Exception {
        String entrada=json.writeValueAsString(new Vincular(receta().codigoReceta()));
        var ruta="/api/me/recetas-demo/vinculacion";
        mvc.perform(post(ruta).contentType("application/json").content(entrada)).andExpect(status().isUnauthorized());
        mvc.perform(post(ruta).header("Authorization",bearer(operador)).contentType("application/json").content(entrada))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/recetas-demo/{id}/pdf",receta().id()).header("Authorization",bearer(paciente)))
            .andExpect(status().isForbidden());
        String token=bearer(paciente);
        cuentas.changeState(paciente.id(),"DESHABILITADA",true);
        mvc.perform(post(ruta).header("Authorization",token).contentType("application/json").content(entrada))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("ACCOUNT_DISABLED"));
        assertEquals(0,numero("SELECT count(*) FROM prescripcion WHERE id_usuario=?",usuario));
    }

    @Test void escaneoCoincidenteVinculaFueraDeLaTransaccionDelProveedor() throws Exception {
        var r=receta();
        when(extractor.disponible()).thenReturn(true);
        when(extractor.extraer(any())).thenAnswer(i -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("image/jpeg",i.<Imagen>getArgument(0).tipo());
            return lectura(r,r.concentracion());
        });
        mvc.perform(multipart("/api/me/recetas-demo/escaneo").file(foto()).header("Authorization",bearer(paciente)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nueva").value(true))
            .andExpect(jsonPath("$.codigo").value("RECETA_VERIFICADA"))
            .andExpect(jsonPath("$.codigoVerificacion").doesNotExist())
            .andExpect(jsonPath("$.prescripcion.codigoExterno").value(r.codigoReceta()));
        mvc.perform(multipart("/api/me/recetas-demo/escaneo").file(foto()).header("Authorization",bearer(paciente)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nueva").value(false));
        mvc.perform(multipart("/api/me/recetas-demo/escaneo").file(foto()).header("Authorization",bearer(otro)))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("RESOURCE_NOT_FOUND"));
        assertEquals(1,numero("SELECT count(*) FROM prescripcion WHERE codigo_externo=?",r.codigoReceta()));
    }

    @Test void escaneoDiscrepanteIlegibleYErrorExternoNoEscriben() throws Exception {
        var r=receta();
        when(extractor.disponible()).thenReturn(true);
        when(extractor.extraer(any())).thenReturn(lectura(r,"999 mcg"))
            .thenReturn(new Lectura(null,null,null,null,null,List.of(),List.of("codigoReceta"),Estado.INCONCLUSA))
            .thenThrow(new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,"ESCANEO_PROVEEDOR_ERROR","Fallo externo"));
        for (var escenario:List.of("RECETA_DEMO_DATOS_NO_COINCIDEN","LECTURA_INCONCLUSA","ESCANEO_PROVEEDOR_ERROR")) {
            mvc.perform(multipart("/api/me/recetas-demo/escaneo").file(foto()).header("Authorization",bearer(paciente)))
                .andExpect(jsonPath("$.codigo").value(escenario));
            sinVinculacion(r);
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"principal","secundario","secundarioEnMinusculas","ausente","emision","vencimiento",
        "medicamento","unidad","presentacion","cantidad"})
    void rechazaLecturasModificadasSinAsignar(String campo) {
        var r=receta();
        var original=lectura(r,r.concentracion());
        var item=new Medicamento(campo.equals("medicamento")?"Medicamento distinto":r.medicamento(),
            campo.equals("unidad")?"100 mg":r.concentracion(),
            campo.equals("presentacion")?"Comprimidos - Caja x 20":r.presentacion(),campo.equals("cantidad")?2:1);
        String secundario=switch (campo) {
            case "ausente" -> null;
            case "secundario" -> original.codigoVerificacion().startsWith("MED-0")
                ? "MED-1"+original.codigoVerificacion().substring(5) : "MED-0"+original.codigoVerificacion().substring(5);
            case "secundarioEnMinusculas" -> original.codigoVerificacion().toLowerCase(java.util.Locale.ROOT);
            default -> original.codigoVerificacion();
        };
        var alterada=new Lectura(campo.equals("principal")?r.codigoReceta().toLowerCase(java.util.Locale.ROOT):r.codigoReceta(),
            secundario,campo.equals("emision")?r.fechaEmision().minusDays(1).toString():original.fechaEmision(),
            campo.equals("vencimiento")?r.fechaVencimiento().plusDays(1).toString():original.fechaVencimiento(),
            original.documento(),List.of(item),List.of(),Estado.COMPLETA);
        String errorEsperado=switch (campo) {
            case "principal" -> "RESOURCE_NOT_FOUND";
            case "secundario","secundarioEnMinusculas" -> "RECETA_DEMO_CODIGO_VERIFICACION_INCORRECTO";
            case "ausente" -> "LECTURA_INCONCLUSA";
            default -> "RECETA_DEMO_DATOS_NO_COINCIDEN";
        };
        assertEquals(errorEsperado,error(()->recetas.verificarYVincular(alterada,paciente,null)).codigo());
        sinVinculacion(r);
    }

    @Test void fotoRecortadaSinVerificacionSeReportaComoLecturaInconclusa() throws Exception {
        var r=receta();
        var original=lectura(r,r.concentracion());
        var pdf=new RecetaDemoPdf().generar(recetas.paraImprimir(r.id(),admin));
        try (var doc=Loader.loadPDF(pdf); var png=new ByteArrayOutputStream()) {
            var imagen=new PDFRenderer(doc).renderImage(0);
            var recorte=imagen.getSubimage(0,0,imagen.getWidth(),590);
            ImageIO.write(recorte,"png",png);
            when(extractor.disponible()).thenReturn(true);
            when(extractor.extraer(any())).thenAnswer(i -> {
                var entrada=i.<Imagen>getArgument(0);
                var preparada=ImageIO.read(new java.io.ByteArrayInputStream(entrada.bytes()));
                assertEquals(590,preparada.getHeight());
                return new Lectura(null,null,original.fechaEmision(),original.fechaVencimiento(),
                    original.documento(),original.medicamentos(),List.of("codigoReceta","codigoVerificacion"),Estado.INCONCLUSA);
            });
            mvc.perform(multipart("/api/me/recetas-demo/escaneo")
                .file(new MockMultipartFile("archivo","recorte.png","image/png",png.toByteArray()))
                .header("Authorization",bearer(paciente)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.codigo").value("LECTURA_INCONCLUSA"));
        }
        sinVinculacion(r);
    }

    @Test void pdfOriginalYAlteradoConservanCodigosYProducenResultadosDistintos() throws Exception {
        var r=receta();
        String concentracionAlterada="200 mcg";
        var coincidencia=lectura(r,r.concentracion());
        var discrepancia=lectura(r,concentracionAlterada);
        byte[] original=descargarPdf(r.id(),"pdf");
        byte[] alterado=descargarPdf(r.id(),"pdf-alterado");
        String textoOriginal=textoPdf(original);
        String textoAlterado=textoPdf(alterado);
        assertTrue(textoOriginal.contains("Concentración: 100 mcg"));
        assertTrue(textoAlterado.contains("Concentración: "+concentracionAlterada));
        assertTrue(textoAlterado.contains("SIMULACIÓN — DOCUMENTO ALTERADO"));
        for (String codigo:List.of(r.codigoReceta(),coincidencia.codigoVerificacion())) {
            assertTrue(textoOriginal.contains(codigo));
            assertTrue(textoAlterado.contains(codigo));
        }
        mvc.perform(get("/api/admin/recetas-demo/{id}/pdf-alterado",r.id())
            .header("Authorization",bearer(paciente))).andExpect(status().isForbidden());
        var controladorApagado=new RecetaDemoAdminController(recetas,new RecetaDemoPdf(),false);
        assertEquals("RESOURCE_NOT_FOUND",error(()->controladorApagado.simulacion(r.id(),admin)).codigo());
        assertEquals(r.concentracion(),repositorio.porId(r.id()).orElseThrow().concentracion());
        assertEquals(1,numero("SELECT count(*) FROM receta_demo WHERE codigo=?",r.codigoReceta()));
        sinVinculacion(r);

        when(extractor.disponible()).thenReturn(true);
        when(extractor.extraer(any())).thenReturn(discrepancia,coincidencia,discrepancia);
        mvc.perform(multipart("/api/me/recetas-demo/escaneo")
            .file(new MockMultipartFile("archivo","alterado.pdf","application/pdf",alterado))
            .header("Authorization",bearer(paciente))).andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("RECETA_DEMO_DATOS_NO_COINCIDEN"));
        sinVinculacion(r);
        mvc.perform(multipart("/api/me/recetas-demo/escaneo")
            .file(new MockMultipartFile("archivo","original.pdf","application/pdf",original))
            .header("Authorization",bearer(paciente))).andExpect(status().isOk())
            .andExpect(jsonPath("$.codigo").value("RECETA_VERIFICADA")).andExpect(jsonPath("$.nueva").value(true));
        mvc.perform(multipart("/api/me/recetas-demo/escaneo")
            .file(new MockMultipartFile("archivo","alterado.pdf","application/pdf",alterado))
            .header("Authorization",bearer(paciente))).andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("RECETA_DEMO_DATOS_NO_COINCIDEN"));
        assertEquals(1,numero("SELECT count(*) FROM prescripcion WHERE codigo_externo=?",r.codigoReceta()));
    }

    @Test void escaneoYVinculacionManualConcurrentesRespetanLaTitularidad() throws Exception {
        var r=receta();
        var lectura=lectura(r,r.concentracion());
        var resultados=concurrentes(()->resultado(()->recetas.verificarYVincular(lectura,paciente,null)),
            ()->resultado(()->reclamar(r,otro)));
        assertEquals(1,resultados.stream().filter("OK"::equals).count());
        assertEquals(1,resultados.stream().filter("RESOURCE_NOT_FOUND"::equals).count());
        assertEquals(1,numero("SELECT count(*) FROM prescripcion WHERE codigo_externo=?",r.codigoReceta()));
    }

    @Test void unaRecetaAnteriorPuedeVincularseSinCodigoSecundario() throws Exception {
        var lote=lote(2);
        var anterior=lote.recetas().getFirst();
        var manual=lote.recetas().getLast();
        db.update("UPDATE receta_demo SET codigo_verificacion=NULL WHERE lote=?",lote.lote());
        assertTrue(textoPdf(descargarPdf(anterior.id(),"pdf")).contains("Sin código secundario (emisión anterior)"));
        assertEquals(Verificacion.RECETA_VERIFICADA_LEGADA,
            recetas.verificarYVincular(lectura(anterior,anterior.concentracion()),paciente,null).codigo());
        assertFalse(reclamar(anterior,paciente).nueva());
        assertTrue(reclamar(manual,paciente).nueva());
    }

    private byte[] descargarPdf(long id,String variante) throws Exception {
        return mvc.perform(get("/api/admin/recetas-demo/{id}/{variante}",id,variante)
            .header("Authorization",bearer(admin))).andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf")).andExpect(header().string("Cache-Control","no-store"))
            .andReturn().getResponse().getContentAsByteArray();
    }

    private String textoPdf(byte[] bytes) throws Exception {
        try (var doc=Loader.loadPDF(bytes)) { return new PDFTextStripper().getText(doc); }
    }

    @Test void archivosInvalidosExcesivosYProveedorNoConfigurado() throws Exception {
        var ruta="/api/me/recetas-demo/escaneo";
        when(extractor.disponible()).thenReturn(false,true,true);
        mvc.perform(multipart(ruta).file(foto()).header("Authorization",bearer(paciente)))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.codigo").value("ESCANEO_NO_CONFIGURADO"));
        mvc.perform(multipart(ruta).file(new MockMultipartFile("archivo","falso.jpg","image/jpeg",new byte[]{1,2,3}))
            .header("Authorization",bearer(paciente))).andExpect(status().isBadRequest());
        mvc.perform(multipart(ruta).file(new MockMultipartFile("archivo","grande.png","image/png",new byte[1048577]))
            .header("Authorization",bearer(paciente))).andExpect(status().is(413));
        verify(extractor,never()).extraer(any());
    }

    @Test void fallaDeAuditoriaReviertePrescripcionEItemYAsignacion() {
        var r=receta();
        AuditService target=AopTestUtils.getUltimateTargetObject(audit);
        doThrow(new IllegalStateException("fallo de prueba")).when(target).record(eq(paciente.id()),eq("USUARIO"),
            eq("VINCULAR_RECETA_DEMO"),eq("RECETA_DEMO"),eq(r.id()),eq(usuario),isNull(),anyMap(),anyMap(),isNull());
        assertThrows(IllegalStateException.class,()->reclamar(r,paciente));
        sinVinculacion(r);
        assertEquals(0,numero("SELECT count(*) FROM item_prescripcion WHERE id_presentacion=?",presentacion));
        assertEquals(0,numero("SELECT count(*) FROM auditoria WHERE actor_cuenta_id=? AND accion='CREAR_PRESCRIPCION'",paciente.id()));
    }

    @Test void colisionDePrescripcionNoDejaVinculacionParcial() {
        var r=receta();
        prescripciones.crear(new ar.edu.uade.medlink.prescripcion.dto.PrescripcionCreateRequest(
            r.codigoReceta(),otroUsuario,r.fechaEmision(),r.fechaVencimiento(),
            List.of(new ar.edu.uade.medlink.prescripcion.dto.PrescripcionCreateRequest.Item(presentacion,1))),admin,null);
        assertEquals("PRESCRIPTION_CODE_ALREADY_EXISTS",error(()->reclamar(r,paciente)).codigo());
        assertEquals(1,numero("SELECT count(*) FROM receta_demo WHERE id_receta_demo=? AND id_usuario IS NULL",r.id()));
        assertEquals(1,numero("SELECT count(*) FROM prescripcion WHERE codigo_externo=? AND id_usuario=?",r.codigoReceta(),otroUsuario));
    }

    @Test void reservaConcurrenteCancelacionRetiroYAgotamientoUsanElFlujoExistente() throws Exception {
        var r=receta();
        long id=reclamar(r,paciente).prescripcion().id();
        var entrada=new CrearReservaRequest(farmacia,presentacion,1,r.codigoReceta());
        var resultados=concurrentes(()->resultado(()->reservas.crear(entrada,UUID.randomUUID(),paciente,null)),
            ()->resultado(()->reservas.crear(entrada,UUID.randomUUID(),paciente,null)));
        assertEquals(1,resultados.stream().filter("OK"::equals).count());
        assertEquals(1,numero("SELECT count(*) FROM reserva WHERE id_usuario=? AND estado_persistido='ACTIVA'",usuario));
        assertEquals(1,numero("SELECT cantidad_comprometida FROM item_prescripcion WHERE id_prescripcion=?",id));
        String codigo=db.queryForObject("SELECT codigo_visible FROM reserva WHERE id_usuario=?",String.class,usuario);
        reservas.cancelarPropia(codigo,paciente,null);
        assertEquals(0,numero("SELECT cantidad_comprometida FROM item_prescripcion WHERE id_prescripcion=?",id));
        assertEquals(10,numero("SELECT cantidad_disponible FROM stock WHERE id_stock=?",stock));
        var nueva=reservas.crear(entrada,UUID.randomUUID(),paciente,null).dto();
        reservas.retirar(farmacia,nueva.codigoVisible(),operador,null);
        assertEquals(1,numero("SELECT cantidad_utilizada FROM item_prescripcion WHERE id_prescripcion=?",id));
        assertEquals(0,numero("SELECT cantidad_comprometida FROM item_prescripcion WHERE id_prescripcion=?",id));
        String agotada=error(()->reservas.crear(entrada,UUID.randomUUID(),paciente,null)).codigo();
        assertEquals("PRESCRIPTION_QUANTITY_INSUFFICIENT",agotada);
        assertEquals(agotada,resultados.stream().filter(s->!s.equals("OK")).findFirst().orElseThrow());
        assertEquals(9,numero("SELECT cantidad_disponible FROM stock WHERE id_stock=?",stock));
        assertEquals("-34.6037000",db.queryForObject("SELECT latitud::text FROM farmacia WHERE id_farmacia=?",String.class,farmacia));
    }

    @Test void migrarDesdeV4YV5ConservaFarmaciasStockReservasYRecetasAnteriores() throws Exception {
        String nombre="medlink_upgrade_"+UUID.randomUUID().toString().replace("-","");
        String url;
        String user;
        try (var conexion=db.getDataSource().getConnection()) {
            url=conexion.getMetaData().getURL().replaceFirst("/[^/?]+(?=\\?|$)","/"+nombre);
            user=conexion.getMetaData().getUserName();
        }
        String clave=container==null?System.getenv().getOrDefault("MEDLINK_TEST_DB_PASSWORD",""):container.getPassword();
        db.execute("CREATE DATABASE "+nombre);
        try {
            var origen=new org.springframework.jdbc.datasource.DriverManagerDataSource(url,user,clave);
            var jdbc=new JdbcTemplate(origen);
            org.flywaydb.core.Flyway.configure().dataSource(origen).target("4").load().migrate();
            jdbc.execute(java.nio.file.Files.readString(java.nio.file.Path.of("../database/02_datos_prueba.sql")));
            jdbc.update("UPDATE farmacia SET latitud=-34.6037,longitud=-58.3816 WHERE id_farmacia=1");
            List<String> tablas=List.of("farmacia","stock","reserva","prescripcion","item_prescripcion");
            var antes=tablas.stream().map(t->filas(jdbc,t)).toList();
            antes.forEach(filas->assertNotNull(filas));
            org.flywaydb.core.Flyway.configure().dataSource(origen).target("5").load().migrate();
            assertEquals(antes,tablas.stream().map(t->filas(jdbc,t)).toList());
            var lote=UUID.randomUUID();
            jdbc.update("""
                INSERT INTO receta_demo(lote,codigo,fecha_emision,fecha_vencimiento,fecha_creacion,id_presentacion,
                    medicamento,concentracion,presentacion)
                VALUES (?,?,'2026-10-01','2026-10-20',now(),9,'Levotiroxina','100 mcg','Comprimidos - Caja x 50')
                """,lote,RecetaDemoService.nuevoCodigo());
            assertEquals(1,jdbc.update("""
                INSERT INTO receta_demo(lote,codigo,estado,fecha_emision,fecha_vencimiento,fecha_creacion,id_presentacion,
                    medicamento,concentracion,presentacion,id_usuario,id_prescripcion,fecha_asignacion)
                SELECT ?,?,'ASIGNADA',p.fecha_emision,p.fecha_vencimiento,p.fecha_hora_creacion,i.id_presentacion,
                    'Levotiroxina','100 mcg','Comprimidos - Caja x 50',p.id_usuario,p.id_prescripcion,p.fecha_hora_creacion
                FROM prescripcion p JOIN item_prescripcion i USING (id_prescripcion) WHERE p.id_prescripcion=1
                LIMIT 1
                """,lote,RecetaDemoService.nuevoCodigo()));
            String snapshot="""
                SELECT jsonb_agg(to_jsonb(r)-'codigo_verificacion' ORDER BY r.id_receta_demo)::text FROM receta_demo r
                """;
            var recetasAnteriores=jdbc.queryForObject(snapshot,String.class);
            org.flywaydb.core.Flyway.configure().dataSource(origen).load().migrate();
            assertEquals(antes,tablas.stream().map(t->filas(jdbc,t)).toList());
            assertEquals(6,jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL",Integer.class));
            assertEquals(recetasAnteriores,jdbc.queryForObject(snapshot,String.class));
            assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM receta_demo WHERE codigo_verificacion IS NULL",Integer.class));
        } finally {
            db.execute("DROP DATABASE "+nombre+" WITH (FORCE)");
        }
    }

    private String filas(JdbcTemplate jdbc,String tabla) {
        return jdbc.queryForObject("SELECT jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text FROM "+tabla+" t",String.class);
    }

    private void sinVinculacion(Receta r) {
        assertEquals(1,numero("SELECT count(*) FROM receta_demo WHERE id_receta_demo=? AND estado='DISPONIBLE' AND id_prescripcion IS NULL",r.id()));
        assertEquals(0,numero("SELECT count(*) FROM prescripcion WHERE codigo_externo=?",r.codigoReceta()));
        assertEquals(0,numero("SELECT count(*) FROM auditoria WHERE accion='VINCULAR_RECETA_DEMO' AND entidad_id=?",r.id()));
    }
    private MockMultipartFile foto() throws Exception {
        var salida=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20,20,BufferedImage.TYPE_INT_RGB),"png",salida);
        return new MockMultipartFile("archivo","receta.png","image/png",salida.toByteArray());
    }
    private Lectura lectura(Receta r,String concentracion) {
        return new Lectura(r.codigoReceta(),repositorio.porId(r.id()).orElseThrow().codigoVerificacion(),
            r.fechaEmision().toString(),r.fechaVencimiento().toString(),
            new Documento(RecetaDemoDocumento.TITULO,RecetaDemoDocumento.LEYENDA,RecetaDemoDocumento.PROFESIONAL,
                RecetaDemoDocumento.INSTITUCION,RecetaDemoDocumento.PACIENTE,RecetaDemoDocumento.INDICACIONES),
            List.of(new Medicamento(r.medicamento(),concentracion,r.presentacion(),1)),List.of(),Estado.COMPLETA);
    }
    private String resultado(Runnable tarea) {
        try { tarea.run(); return "OK"; }
        catch (ApiException ex) { return ex.codigo(); }
    }
    private List<String> concurrentes(Callable<String> primera,Callable<String> segunda) throws Exception {
        try (var pool=Executors.newFixedThreadPool(2)) {
            var inicio=new CountDownLatch(1);
            var a=pool.submit(()->{inicio.await(); return primera.call();});
            var b=pool.submit(()->{inicio.await(); return segunda.call();});
            inicio.countDown();
            return List.of(a.get(8,TimeUnit.SECONDS),b.get(8,TimeUnit.SECONDS));
        }
    }
}
