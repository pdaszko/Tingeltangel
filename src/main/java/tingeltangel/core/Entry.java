
package tingeltangel.core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;

import tingeltangel.core.scripting.SyntaxError;
import tingeltangel.tools.FileEnvironment;

public class Entry {

    private final static int EMPTY = 0;
    private final static int MP3 = 1;
    private final static int CODE = 2;
    private final static int SUB = 3;
    
    private File mp3 = null;
    private Script script = null;
    private float mp3length = -1;
    private int size = -1;
    private Book book;
    private String hint = "";
    private int type = EMPTY;
    private int tingID = -1;
    
    public Entry(Book book, int tingID) {
        this.book = book;
        this.tingID = tingID;
    }
    
    public Book getBook() {
        return(book);
    }
    
    public int getTingID() {
        return(tingID);
    }
    
    void save(DataOutputStream out) throws IOException {
        out.writeInt(tingID);
        if(mp3 == null) {
            out.writeUTF("");
        } else {
            out.writeUTF(mp3.getName());
        }
        
        if(script == null) {
            writeLongUTF(out, "");
        } else {
            writeLongUTF(out, script.toString());
        }
        out.writeInt(type);
        out.writeUTF(hint);
    }
    
    public int getSize() throws SyntaxError {
        if(isMP3()) {
            return(size);
        } else if((isCode() || isSub()) && (script != null)) {
            return(script.getSize(false));
        }
        return(0);
    }
    
    public static void writeLongUTF(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(Charset.forName("UTF8"));
        out.writeInt(b.length);
        out.write(b);
    }
    
    public static String readLongUTF(DataInputStream in) throws IOException {
        int length = in.readInt();
        byte[] b = new byte[length];
        int off = 0;
        while(off < length) {
            off += in.read(b, off, length - off);
        }
        return(new String(b, Charset.forName("UTF8")));
        
    }
    
    public String getHint() {
        return(hint);
    }
    
    public void setHint(String hint) {
        this.hint = hint.trim();
        changeMade();
    }
    
    static Entry load(DataInputStream in, Book book) throws IOException {
        Entry entry = new Entry(book, in.readInt());
        
        String sMp3 = in.readUTF();
        
        
        if(!sMp3.isEmpty()) {
            entry.mp3 = new File(FileEnvironment.getAudioDirectory(book.getID()), sMp3);
            if(!entry.mp3.canRead()) {
                entry.mp3 = null;
            } else {
                entry.size = (int)entry.mp3.length();
            }
            entry.mp3length = Mp3Utils.getDuration(entry.mp3) / 1000.f;
        }
        
        String sScript = readLongUTF(in);
        if(!sScript.isEmpty()) {
            entry.script = new Script(sScript, entry);
        }
        
        entry.type = in.readInt();
        entry.hint = in.readUTF();
        
        return(entry);
    }
    
    void changeMade() {
        book.changeMade();
    }
            
    public File getMP3() {
        return(mp3);
    }
    
    public boolean isMP3() {
        return(type == MP3);
    }
    
    public boolean isCode() {
        return(type == CODE);
    }
    
    public boolean isEmpty() {
        return(type == EMPTY);
    }
    
    public boolean isSub() {
        return(type == SUB);
    }
    
    public void setEmpty() {
        type = EMPTY;
        changeMade();
    }
    
    public void setMP3() {
        type = MP3;
        changeMade();
    }
    
    public void setCode() {
        type = CODE;
        changeMade();
    }
    
    public void setSub() {
        type = SUB;
        changeMade();
    }
    
    public float getLength() {
        return(mp3length);
    }
    
    public boolean hasCode() {
        return(!isEmpty());
    }
    
    public void setScript(Script script) {
        this.script = script;
        mp3 = null;
        type = CODE;
        changeMade();
    }
    
    public Script getScript() {
        return(script);
    }
    
    public void setMP3(File mp3) throws IOException {
        changeMade();
        String name = mp3.getAbsolutePath();
        if(!mp3.isFile()) {
            this.mp3 = null;
            mp3length = -1;
            throw new FileNotFoundException(name);
        }
        if(!mp3.canRead()) {
            this.mp3 = null;
            mp3length = -1;
            throw new FileNotFoundException(name);
        }
        
        // copy to book/audio dir if it is not already there
        File target = new File(FileEnvironment.getAudioDirectory(book.getID()), mp3.getName());
        if(!mp3.equals(target)) {
        	FileEnvironment.copy(mp3, target);
        }

        this.mp3 = target;
        try {
            mp3length = Mp3Utils.getDuration(this.mp3) / 1000.f;
        } catch(IOException e) {
            this.mp3 = null;
            throw e;
        }
        size = (int)mp3.length();
        script = null;
        type = MP3;
    }
}
