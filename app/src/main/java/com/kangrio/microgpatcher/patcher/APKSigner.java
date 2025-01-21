package com.kangrio.microgpatcher.patcher;

import android.content.Context;
import android.os.Build;

import com.android.apksig.ApkSigner;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collections;

public class APKSigner {

    private final Context mContext;

    public APKSigner(Context context) {
        mContext = context;
    }

    private static PrivateKey getPrivateKey(File keyFile) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        FileInputStream fis = new FileInputStream(keyFile);
        DataInputStream dis = new DataInputStream(fis);
        byte[] keyBytes = new byte[(int) keyFile.length()];
        dis.readFully(keyBytes);
        dis.close();
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf =
                KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    private static X509Certificate getCertificate() throws CertificateException {
        String certificateString;
        certificateString = "-----BEGIN CERTIFICATE-----\n" +
                "MIICyTCCAbGgAwIBAgIEMzoLmzANBgkqhkiG9w0BAQsFADAUMRIwEAYDVQQGEwlkZWJ1Z2dpbmcw\n" +
                "IBcNMTYwOTIzMTE1NzA2WhgPMzAxNTAxMjUxMTU3MDZaMBQxEjAQBgNVBAYTCWRlYnVnZ2luZzCC\n" +
                "ASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAK7lP14jM1wBxzH33gLz+SUTg1v2aQsVRK1b\n" +
                "/k2rgyR3BhnXJ65uRfGCalxr81BNuzK9SANdkQwiQbz8mQl0jlSr6dYODGckF/bR+lW/FMygXQXs\n" +
                "i82LwV8o7DcxIMRE5C6/JuIgtWKiPpbjpF1a6gGjcY7pU5ccU0lwPgTm1w7RNxg5XstIszo8EW9T\n" +
                "rYUoEdt77xh70hJZqcMVQtWAkFtnqQMNnQ7ovPO7gUvclsTNNN/cjVjfRdijMAeoKGTn52c5G262\n" +
                "SoTSbpOvbgq7dcMxLVa6eUAP1WS+ICVEQGN/9dMsauIhKn0pZldOpUaD1go+rGx9QFBnlpYCMI1n\n" +
                "Zr8CAwEAAaMhMB8wHQYDVR0OBBYEFBxOxDlug4QN/L8mWZMb+tU6EzdpMA0GCSqGSIb3DQEBCwUA\n" +
                "A4IBAQBfSNgRse84Y0AQGHXIW/825hjX+7x7qsFEnDsIJOsIh5Ccb7L2QP+Z5ZwSI5UrnnAMb3xq\n" +
                "KC0iu/CgKhgAeCY6MFu0LNh39RO1IwibEaS7hnvoO+avQgLRCL7OE5sGhcaz3Cfvd6tnAG4c630s\n" +
                "PlCHaV2gLv2a6PO/uNnr8d2l5PkN9xoT5Qd5tkEarLb60Jhn8zM7VXKadv93awaldHbeIb2KDBpZ\n" +
                "mmG4/ASA7EMWFZ6Mq7iJBKx4PLo8KxOjEe5jkPIkDxPpSSHdtowNHkE3OL0HaYTG2v44uhOrSFWC\n" +
                "bOAhcBvp8jQYTvANivDYnMUn1Jna11yo0yrP531Rr6jP\n" +
                "-----END CERTIFICATE-----";
        InputStream inputStream = new ByteArrayInputStream(certificateString.getBytes());
        return (X509Certificate) CertificateFactory
                .getInstance("X509")
                .generateCertificate(inputStream);
    }

    public void sign(File apkFile, File output) throws Exception {
        File keyFile = new File(mContext.getExternalFilesDir("signing"), "debugging.p8");
        if (!keyFile.exists()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Files.copy(mContext.getAssets().open("debugging.p8"), keyFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder("CERT", getPrivateKey(
                new File(mContext.getExternalFilesDir("signing"), "debugging.p8")), Collections.singletonList(getCertificate(
        ))).build();
        ApkSigner.Builder builder = new ApkSigner.Builder(Collections.singletonList(signerConfig));
        builder.setInputApk(apkFile);
        builder.setOutputApk(output);
        builder.setCreatedBy("debugging");
        builder.setV1SigningEnabled(true);
        builder.setV2SigningEnabled(true);
        builder.setV3SigningEnabled(true);
        builder.setMinSdkVersion(-1);
        ApkSigner signer = builder.build();
        signer.sign();
    }

}