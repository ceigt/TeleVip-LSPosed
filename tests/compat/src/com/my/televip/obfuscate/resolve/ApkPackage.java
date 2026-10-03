package com.my.televip.obfuscate.resolve;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** The package name in an APK's binary AndroidManifest.xml. */
final class ApkPackage {

    private ApkPackage() {
    }

    static String of(File apk) throws Exception {
        byte[] xml;
        try (ZipFile zip = new ZipFile(apk)) {
            ZipEntry entry = zip.getEntry("AndroidManifest.xml");
            if (entry == null) return null;
            try (InputStream in = zip.getInputStream(entry)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    if (out.size() + n > 8 * 1024 * 1024) throw new IllegalArgumentException("Manifest too large");
                    out.write(buffer, 0, n);
                }
                xml = out.toByteArray();
            }
        }
        ByteBuffer b = ByteBuffer.wrap(xml).order(ByteOrder.LITTLE_ENDIAN);
        // String pool: the first chunk after the 8-byte file header.
        int pool = 8;
        if (xml.length < 36 || (b.getShort(pool) & 0xffff) != 1) throw new IllegalArgumentException("Invalid string pool");
        int poolSize = b.getInt(pool + 4);
        int stringCount = b.getInt(pool + 8);
        if (poolSize < 28 || poolSize > xml.length - pool || stringCount < 0 || stringCount > (poolSize - 28) / 4) throw new IllegalArgumentException("Invalid string pool bounds");
        int flags = b.getInt(pool + 16);
        int stringsStart = b.getInt(pool + 20);
        boolean utf8 = (flags & 0x100) != 0;
        String[] strings = new String[stringCount];
        for (int i = 0; i < stringCount; i++) {
            int p = pool + stringsStart + b.getInt(pool + 28 + 4 * i);
            if (utf8) {
                p += (xml[p] & 0x80) != 0 ? 2 : 1;                    // utf-16 length
                int len = xml[p] & 0xFF;
                if ((len & 0x80) != 0) {
                    len = ((len & 0x7F) << 8) | (xml[p + 1] & 0xFF);
                    p += 2;
                } else {
                    p += 1;
                }
                strings[i] = new String(xml, p, len, StandardCharsets.UTF_8);
            } else {
                int len = b.getShort(p) & 0xFFFF;
                p += 2;
                if ((len & 0x8000) != 0) {len = ((len & 0x7fff) << 16) | (b.getShort(p) & 0xffff);p += 2;}
                if (len < 0 || len > (xml.length - p) / 2) throw new IllegalArgumentException("Invalid UTF16 length");
                strings[i] = new String(xml, p, 2 * len, StandardCharsets.UTF_16LE);
            }
        }
        // The first start-element is <manifest>; its "package" attribute.
        for (int p = pool + poolSize; p + 8 <= xml.length; ) {
            int type = b.getShort(p) & 0xFFFF, size = b.getInt(p + 4);
            if (size < 8 || size > xml.length - p) throw new IllegalArgumentException("Invalid XML chunk");
            if (type == 0x0102 && "manifest".equals(strings[b.getInt(p + 20)])) {
                int count = b.getShort(p + 28) & 0xFFFF;
                for (int i = 0; i < count; i++) {
                    int a = p + 36 + 20 * i;
                    if ("package".equals(strings[b.getInt(a + 4)])) {
                        int raw = b.getInt(a + 8);
                        return raw >= 0 ? strings[raw] : null;
                    }
                }
                return null;
            }
            if (size <= 0) break;
            p += size;
        }
        return null;
    }
}
