package ar.edu.uade.medilink.android.cloudtest

import androidx.lifecycle.ViewModelProvider
import ar.edu.uade.medilink.android.MainActivity
import ar.edu.uade.medilink.android.MediLinkAppVm
import okhttp3.Call
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.IdentityHashMap

/** Test-process observer only: delegates every real call unchanged, never reads bodies/headers. */
internal class CloudRequestProbe(activity: MainActivity) {
    private data class Attempt(val host: String,val path: String,val method: String,val multipart: Boolean)
    private val attempts=Collections.synchronizedList(mutableListOf<Attempt>())
    private val originals=IdentityHashMap<Retrofit,Call.Factory>()
    init {
        val repository=ViewModelProvider(activity)[MediLinkAppVm::class.java].repository
        for(name in listOf("api","parity","recetas","escaneo")) {
            val service=repository.javaClass.getDeclaredField(name).apply{isAccessible=true}.get(repository)
            val handler=Proxy.getInvocationHandler(service)
            val retrofit=handler.javaClass.declaredFields.first{it.type==Retrofit::class.java}.apply{isAccessible=true}.get(handler) as Retrofit
            if(originals.containsKey(retrofit))continue
            val original=retrofit.callFactory();originals[retrofit]=original
            val observer=object:Call.Factory {
                override fun newCall(request:Request):Call {
                    attempts.add(Attempt(request.url.host,request.url.encodedPath,request.method,
                        request.body?.contentType()?.type=="multipart"))
                    return original.newCall(request)
                }
            }
            Retrofit::class.java.getDeclaredField("callFactory").apply{isAccessible=true}.set(retrofit,observer)
        }
        check(originals.size==2){"Both ordinary and scan HTTP clients must be observed"}
    }
    fun verifyAndReport():JSONObject {
        val seen=synchronized(attempts){attempts.toList()}
        check(seen.any{it.path=="/api/auth/login"}){"Probe did not observe the positive login control"}
        check(seen.any{it.path=="/api/me/recetas-demo/configuracion"}){"Probe did not observe live scan configuration"}
        check(seen.none{it.multipart||it.path.contains("/escaneo")||it.path.contains("recetas-adjuntas")}){"Document transfer attempted with scanning disabled"}
        check(seen.all{it.host=="medifind-api-ggian102-mey9.onrender.com"}){"Unexpected API destination"}
        check(seen.none{it.method!="GET"&&it.path !in setOf("/api/auth/login","/api/auth/logout")}){"Unexpected mutation during local capture"}
        return JSONObject().put("httpClientsObserved",originals.size).put("positiveControlsObserved",true)
            .put("documentTransferAttempts",0).put("openAiRequests",0)
            .put("requests",JSONArray(seen.map{JSONObject().put("path",it.path).put("method",it.method)}))
    }
    fun close(){
        for((retrofit,original)in originals)Retrofit::class.java.getDeclaredField("callFactory").apply{isAccessible=true}.set(retrofit,original)
    }
}
