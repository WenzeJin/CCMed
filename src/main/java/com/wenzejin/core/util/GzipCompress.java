package com.wenzejin.core.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipCompress {
    // 压缩字符串
    public static String compressString(String str) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
            gzipOutputStream.write(str.getBytes());
        }

        // 使用 BASE64 编码压缩后的字节流，以便存储和传输
        return Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray());
    }

    // 解压字符串
    public static String decompressString(String compressedStr) throws IOException {
        byte[] compressedData = Base64.getDecoder().decode(compressedStr);
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(compressedData);
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream);
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = gzipInputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }

            return byteArrayOutputStream.toString();  // 返回解压缩后的字符串
        }
    }

    // 压缩字符串为字节数组
    public static byte[] compressStringByte(String str) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
            gzipOutputStream.write(str.getBytes());
            gzipOutputStream.close();
            return byteArrayOutputStream.toByteArray();
        }
    }

    // 解压字节数组为字符串
    public static String decompressString(byte[] compressedData) throws IOException {
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(compressedData);
             GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream);
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = gzipInputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
            return byteArrayOutputStream.toString();  // 返回解压后的字符串
        }
    }

    public static void main(String[] args) throws IOException {
        String originalString = "This is a long string that will be compressed using GZIP! This is a long string that will be compressed using GZIP!This is a long string that will be compressed using GZIP!This is a long string that will be compressed using GZIP!This is a long string that will be compressed using GZIP!This is a long string that will be compressed using GZIP!This is a long string that will be compressed using GZIP!This is a long string that will be compressed using GZIP!This is a long string that will be compressed using GZIP!";

        // 压缩字符串
        String compressedString = compressString(originalString);
        System.out.println("Compressed String: " + compressedString);

        // 解压缩字符串
        String decompressedString = decompressString(compressedString);
        System.out.println("Decompressed String: " + decompressedString);
    }
}