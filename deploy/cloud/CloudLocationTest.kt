package ar.edu.uade.medilink.android.cloudtest

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.edu.uade.medilink.android.MainActivity
import java.util.concurrent.atomic.AtomicBoolean
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Emulator-only synthetic fixes; never obtains or publishes a person's actual location. */
@RunWith(AndroidJUnit4::class)
class CloudLocationTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun waitText(text:String){ui.waitUntil(120_000){runCatching{ui.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}}
    private fun findMap(v:View):MapView?{if(v is MapView)return v;if(v is ViewGroup)for(i in 0 until v.childCount)findMap(v.getChildAt(i))?.let{return it};return null}
    @Suppress("DEPRECATION")
    @Test fun approximateAndPrecisePermissionDrawSyntheticCurrentLocation(){
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        assertTrue(android.os.Build.MODEL.contains("sdk_gphone"))
        val manager=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.ACCESS_COARSE_LOCATION)
        assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION))
        manager.addTestProvider(LocationManager.NETWORK_PROVIDER,false,false,true,false,false,false,false,Criteria.POWER_LOW,Criteria.ACCURACY_FINE)
        manager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER,true)
        val running=AtomicBoolean(true)
        val producer=Thread{
            while(running.get()){
                try{manager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER,Location(LocationManager.NETWORK_PROVIDER).apply{
                    latitude=-34.6112;longitude=-58.4321;accuracy=10f;time=System.currentTimeMillis();elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()
                });Thread.sleep(500)}catch(_:InterruptedException){return@Thread}
            }
        }.apply{isDaemon=true;start()}
        try{
            waitText("Entrar como invitado");ui.onNodeWithText("Entrar como invitado").performClick()
            waitText("Buscar");ui.onNodeWithText("Buscar").performClick();waitText("Amoxicilina")
            ui.onAllNodesWithText("Amoxicilina",substring=true).onFirst().performClick()
            ui.waitUntil(60_000){runCatching{ui.onAllNodesWithContentDescription("Consultar disponibilidad").fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}
            ui.onAllNodesWithContentDescription("Consultar disponibilidad").onFirst().performClick()
            waitText("Tu ubicación aproximada")
            waitText("Ver en mapa");ui.onAllNodesWithText("Ver en mapa",substring=true).onFirst().performScrollTo().performClick()
            waitText("Tu ubicación aproximada")
            ui.onNodeWithContentDescription("Mi ubicación").performClick();waitText("Tu ubicación aproximada")
            assertEquals(context.packageName,instrumentation.uiAutomation.rootInActiveWindow.packageName.toString())
            var markers=0;ui.waitUntil(60_000){
                ui.runOnUiThread{markers=findMap(ui.activity.window.decorView)?.overlays?.filterIsInstance<Marker>()?.size?:0};markers>=2
            }
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.ACCESS_FINE_LOCATION)
            ui.onNodeWithContentDescription("Mi ubicación").performClick()
            waitText("Tu ubicación (±10 m)")
            var exactOrigin=false;ui.waitUntil(30_000){ui.runOnUiThread{exactOrigin=findMap(ui.activity.window.decorView)?.overlays?.filterIsInstance<Marker>()?.any{
                kotlin.math.abs(it.position.latitude-(-34.6112))<0.000001&&kotlin.math.abs(it.position.longitude-(-58.4321))<0.000001
            }==true};exactOrigin}
        }finally{
            running.set(false);producer.interrupt();producer.join(2000)
            manager.removeTestProvider(LocationManager.NETWORK_PROVIDER)
            // The host restores runtime permissions after instrumentation exits;
            // revoking here can terminate the target process before JUnit reports.
        }
    }
}
