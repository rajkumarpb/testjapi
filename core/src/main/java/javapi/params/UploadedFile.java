package javapi.params;

import java.util.Arrays;

public record UploadedFile(String name, String filename, String contentType, byte[] content) {

    public long size() {
        return content.length;
    }

    @Override
    public String toString() {
        return "UploadedFile[name=" + name + ", filename=" + filename
                + ", contentType=" + contentType + ", size=" + content.length + "]";
    }

    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
