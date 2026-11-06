package de.naturfoto.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.osmdroid.config.Configuration
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        de.naturfoto.app.ebird.ContextHolder.ctx = this
        de.naturfoto.app.theme.AppContextHolder.context = this
        Configuration.getInstance().apply {
            load(this@MainActivity, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this@MainActivity))
            userAgentValue = packageName
            osmdroidBasePath = filesDir
            osmdroidTileCache = java.io.File(filesDir, "osmdroid-cache")
            Locale.setDefault(Locale.GERMANY)
        }
        setContent { NaturfotoApp() }
    }
}
