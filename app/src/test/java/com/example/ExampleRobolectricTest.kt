package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExampleRobolectricTest {

    @Test
    fun `manifest, strings, backup rules, and network security config are hardened`() {
        val projectAppDir = if (File("src/main").exists()) File("src/main") else File("app/src/main")

        val stringsXml = File(projectAppDir, "res/values/strings.xml").readText()
        assertTrue(stringsXml.contains("<string name=\"app_name\">JARVIS</string>"))

        val manifestXml = File(projectAppDir, "AndroidManifest.xml").readText()
        assertTrue(manifestXml.contains("android:usesCleartextTraffic=\"false\""))
        assertTrue(manifestXml.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
        assertFalse(manifestXml.contains("READ_EXTERNAL_STORAGE"))
        assertFalse(manifestXml.contains("SYSTEM_ALERT_WINDOW"))

        val netSecXml = File(projectAppDir, "res/xml/network_security_config.xml").readText()
        assertTrue(netSecXml.contains("cleartextTrafficPermitted=\"false\""))
        assertTrue(netSecXml.contains("127.0.0.1"))

        val backupXml = File(projectAppDir, "res/xml/backup_rules.xml").readText()
        assertTrue(backupXml.contains("jarvis_secure_keys.xml"))
    }
}
