package xyz.nulldev.androidcompat.pm

import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.os.Bundle
import com.android.apksig.ApkVerifier
import com.googlecode.d2j.dex.Dex2jar
import net.dongliu.apk.parser.ApkFile
import net.dongliu.apk.parser.ApkParsers
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory



data class InstalledPackage(val root: File) {
    val apk = File(root, "package.apk")
    val jar = File(root, "translated.jar")
    val icon = File(root, "icon.png")

    val info: PackageInfo
        get() = ApkParsers.getMetaInfo(apk).toPackageInfo(root).also {
            val parsed = ApkFile(apk)
            val dbFactory = DocumentBuilderFactory.newInstance()
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = parsed.manifestXml.byteInputStream().use {
                dBuilder.parse(it)
            }

            it.applicationInfo.metaData = Bundle().apply {
                val appTag = doc.getElementsByTagName("application").item(0)

                appTag?.childNodes?.toList()?.filter {
                    it.nodeType == Node.ELEMENT_NODE
                }?.map {
                    it as Element
                }?.filter {
                    it.tagName == "meta-data"
                }?.map {
                    putString(it.attributes.getNamedItem("android:name").nodeValue,
                            it.attributes.getNamedItem("android:value").nodeValue)
                }
            }

            (parsed.apkSingers.flatMap { it.certificateMetas }
            + parsed.apkV2Singers.flatMap { it.certificateMetas })
                    .map { Signature(it.data) }
        }

    fun verify(): Boolean {
        val res = ApkVerifier.Builder(apk)
                .build()
                .verify()

        return res.isVerified
    }

    fun writeIcon() {
        try {
            val icons = ApkFile(apk).allIcons

            val iconMeta = icons.find { it.isFile } ?: return
            val read = iconMeta.data.inputStream().use {
                ImageIO.read(it)
            }

            ImageIO.write(read, "png", icon)
        } catch(e: Exception) {
            icon.delete()
        }
    }

    fun writeJar() {
        try {
            Dex2jar.from(apk).to(jar.toPath())
        } catch(e: Exception) {
            jar.delete()
        }
    }

    private fun NodeList.toList(): List<Node> {
        val out = mutableListOf<Node>()

        for(i in 0 until length)
            out += item(i)

        return out
    }
}