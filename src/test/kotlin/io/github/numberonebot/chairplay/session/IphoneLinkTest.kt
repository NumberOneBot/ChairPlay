package io.github.numberonebot.chairplay.session

import io.github.numberonebot.chairplay.config.configWith
import io.github.numberonebot.chairplay.platform.FakeBluetooth
import io.github.numberonebot.chairplay.platform.FakeRfcommLink
import io.github.numberonebot.chairplay.platform.PairedDevice
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Date
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERBitString
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x509.Time
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** The way to the iPhone's iAP2 service: which paired device it picks, and the stream over its RFCOMM link. */
class IphoneLinkTest {
    private val airPods = PairedDevice("AirPods", "AA:BB:CC:DD:EE:02", 0x240418)
    private val iphone = PairedDevice("iPhone", "AA:BB:CC:DD:EE:01", 0x7A020C)
    private val bluetooth = FakeBluetooth(paired = listOf(airPods, iphone))

    @Test
    fun `the iPhone link opens the iAP2 service on the paired phone`() {
        openIphoneLink(bluetooth, null) {}.close()
        assertEquals(listOf(iphone.address to IAP2_IPHONE_UUID), bluetooth.connected)
    }

    @Test
    fun `a configured address picks that device`() {
        openIphoneLink(bluetooth, "aa:bb:cc:dd:ee:02") {}.close()
        assertEquals(listOf(airPods.address to IAP2_IPHONE_UUID), bluetooth.connected)
    }

    // Closing the stream frees the link, so a reconnect after a Wi-Fi drop finds the iPhone free.
    @Test
    fun `the iPhone stream carries the link's bytes and closes it`() {
        val link = FakeRfcommLink()
        bluetooth.link = { link }
        val stream = openIphoneLink(bluetooth, null) {}
        stream.send(byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), link.output.toByteArray())
        link.receive(byteArrayOf(4, 5))
        assertArrayEquals(byteArrayOf(4, 5), stream.recv(16, 5_000))
        stream.close()
        stream.close()
        assertEquals(1, link.closes)
    }

    @Test
    fun `the radio address is the platform's`() {
        val config = configWith()
        writeMfiFiles(config.mfiDirectory())
        bluetooth.radio = "11:22:33:44:55:66"
        assertEquals("11:22:33:44:55:66", DesktopParts(config, {}, bluetooth).radioAddress())
    }

    /** The two files LocalMfiAuthenticationClient loads: a P-256 key and a certificate for it, signed by itself. */
    private fun writeMfiFiles(directory: File) {
        val keys = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }
        val name = X500Name("CN=ChairPlay test")
        val algorithm = AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA256)
        val certificate = V3TBSCertificateGenerator().apply {
            setSerialNumber(ASN1Integer(BigInteger.ONE))
            setIssuer(name)
            setSubject(name)
            setStartDate(Time(Date(0)))
            setEndDate(Time(Date(2_000_000_000_000)))
            setSignature(algorithm)
            setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(keys.public.encoded))
        }.generateTBSCertificate()
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keys.private)
            update(certificate.encoded)
            sign()
        }
        directory.mkdirs()
        File(directory, "identity.pk8").writeBytes(keys.private.encoded)
        val signed = arrayOf<ASN1Encodable>(certificate, algorithm, DERBitString(signature))
        File(directory, "certificate.p7b").writeBytes(DERSequence(signed).encoded)
    }
}
