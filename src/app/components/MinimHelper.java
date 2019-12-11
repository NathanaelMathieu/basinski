package app.components;

import java.io.*;

public class MinimHelper {

    public String sketchPath( String localPath ) {
        return "B:\\Development\\Eclipse\\basinski\\src\\" + localPath;
    }

    public InputStream createInput(String fileName) {
        InputStream is = null;
        try {
            is = new FileInputStream(sketchPath(fileName));
        }
        catch(Exception e) {
            System.out.println(e.toString());
        }
        return is;
    }
}