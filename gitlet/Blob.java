package gitlet;


import java.io.Serializable;

public class Blob implements Serializable {
    /** store file's content. */
    private final byte[] content;

    public Blob(byte[] content) {
        this.content = content;
    }

    public byte[] getContent() {
        return content;
    }
}
