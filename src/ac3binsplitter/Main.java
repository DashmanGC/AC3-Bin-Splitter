/*
 * Copyright (C) 2014 Dashman
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ac3binsplitter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Jonatan
 */
public class Main {

    public static class IndexEntry{
        public String name;
        public int offset;
        public int size;

        public IndexEntry(){
            name = "";
            offset = 0;
            size = 0;
        }

        public IndexEntry(String n, int o, int s){
            name = n;
            offset = o;
            size = s;
        }
    }

    static String filename;
    static String destination = ".";
    static RandomAccessFile f;
    static String file_list = "";
    static byte[] seq;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
        /*
         * USE
         * -s <filename> [<destination_folder>] Splits filename's contents on destination
         * -m <filename> <files_list> Merges the list of files in files_list into filename
         */

        boolean show_use = false;

        if (args.length < 2 || args.length > 3){
            show_use = true;
        }

        else{
            String command = args[0];
            filename = args[1];

            if (command.equals("-s")){

                if (args.length == 3)
                    destination = args[2];

                // Try opening the file
                try{
                    f = new RandomAccessFile(filename, "r");
                    // Read the header / index and obtain the offsets
                    readHeader();
                }catch (IOException ex) {
                    System.err.println("ERROR: Couldn't read file.");   // END
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            else if (command.equals("-m")){
                if (args.length != 3)
                    show_use = true;
                else{
                    file_list = args[2];
                    // Read the file list and merge the contents into the given filename
                    try{
                        mergeFileList();
                    }catch (IOException ex) {
                        System.err.println("ERROR: Couldn't read file.");   // END
                        Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            else    // Wrong command
                show_use = true;
        }

        if (show_use){
            System.out.println("ERROR: Wrong number of parameters: " + args.length);
            System.out.println("TO SPLIT:\n java -jar bin_splitter -s <filename> [<destination_folder>]");
            System.out.println("TO MERGE:\n java -jar bin_splitter -m <filename> <files_list>");
        }

    }

    public static void readHeader() throws IOException{
        // Read the first 4 bytes of the file
        byte[] header = new byte[4];
        f.read(header);

        // If the first byte isn't 00, we might have a valid file
        if (header[0] != 0){
            int index_size = header[0] & 0xff; // Take the lower part
            index_size += ((header[1] & 0xff) << 8);   // Add the upper part

            //System.out.println("Index size: " + index_size);

            getIndex4(index_size);

            // Write the file list
            writeFileList();
        }
        // Otherwise, indicate the file is not supported
        else{
            System.err.println("ERROR: Unsupported file."); // END
            f.close();
        }
    }

    // Takes a 4-byte hex little endian and returns its int value
    public static int byteSeqToInt(byte[] byteSequence){
        if (byteSequence.length != 4)
            return -1;

        int value = 0;
        value += byteSequence[0] & 0xff;
        value += (byteSequence[1] & 0xff) << 8;
        value += (byteSequence[2] & 0xff) << 16;
        value += (byteSequence[3] & 0xff) << 24;
        return value;
    }

    // Receives an int and return its 4-byte value
    public static byte[] int2bytes(int value){
        return new byte[] {
                (byte) value,
                (byte)(value >>> 8),
                (byte)(value >>> 16),
                (byte)(value >>> 24)};
    }

    public static void getIndex4(int num_entries) throws IOException{
        // Prepare an arraylist of IndexEntry
        ArrayList<IndexEntry> entries = new ArrayList<IndexEntry>();

        IndexEntry ie;
        String name = "";
        int offset;
        int next;
        int size = 0;
        boolean go_on = true;

        for (int i = 0; i < num_entries && go_on; i++){
            // Every entry in the index has 4 bytes indicating its offset
            // The last entry points at an "end" file that is 32 bytes long and has nothing
            f.seek( (i + 1) * 4 );    // Go to the beginning of our current entry
            byte[] entry_block = new byte[8];   // Read the offset of this entry and the next one
            f.read(entry_block);

            if (i < 10)
                name = "000" + i;
            else if (i < 100)
                name = "00" + i;
            else if (i < 1000)
                name = "0" + i;
            else
                name = "" + i;

            seq = new byte[4];
            seq[0] = entry_block[0];
            seq[1] = entry_block[1];
            seq[2] = entry_block[2];
            seq[3] = entry_block[3];
            offset = byteSeqToInt(seq);

            seq[0] = entry_block[4];
            seq[1] = entry_block[5];
            seq[2] = entry_block[6];
            seq[3] = entry_block[7];
            next = byteSeqToInt(seq);

            if (i == num_entries - 1){
                size = (int) f.length() - offset;
                go_on = false;
            }
            else
                size = next - offset;

            ie = new IndexEntry(name, offset, size);

            entries.add(ie);
        }

        // Extract every file in the final list
        for (int i = 0; i < entries.size(); i++){
            //System.out.println(i + " - Offset: " + entries.get(i).offset + " Size: " + entries.get(i).size);
            extractFile(entries.get(i));

            //file_list += entries.get(i).name;
            if (i != entries.size() - 1)
                file_list += "\n";
        }

        // Inform of results
        System.out.println("Finished. Extracted " + entries.size() + " files.");

        f.close();  // END
    }

    public static void extractFile(IndexEntry ie) throws IOException{
        f.seek( (long) ie.offset);

        seq = new byte[ie.size];

        f.read(seq);

        String path = "";

        ie.name += ".tim";

        if (destination.equals("."))
            path = ie.name;
        else{
        // Check if folder with the name of the pak_file exists. If not, create it.
            path = destination;
            File folder = new File(path);
            if (!folder.exists()){
                boolean success = folder.mkdir();
                if (!success){
                    System.err.println("ERROR: Couldn't create folder.");
                    return;
                }
            }
            path += "/" + ie.name;
        }

        // Create the file inside said folder
        try {
            RandomAccessFile f2 = new RandomAccessFile(path, "rw");

            f2.write(seq);

            f2.close();

            file_list += ie.name;

            //System.out.println(ie.name + " saved successfully.");
        } catch (IOException ex) {
            System.err.println("ERROR: Couldn't write " + ie.name);
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void writeFileList() throws IOException{
        String path = "";
        if (destination.equals("."))
            path = "files.list";
        else    // The folder was created previously
            path = destination + "/files.list";

        PrintWriter pw = new PrintWriter(path);

        pw.print(file_list);

        pw.close();
    }

    public static void mergeFileList() throws IOException{
        BufferedReader br = new BufferedReader(new FileReader(file_list));
        String line;
        int entry_size = 4;
        int table_size = 4;
        int total_length = 0;
        ArrayList<IndexEntry> entries = new ArrayList<IndexEntry>();

        IndexEntry ie;

        // Read all filenames in files.list and their sizes
        int actual_length = 0;
        int padded_length = 0;

        while ((line = br.readLine()) != null) {

            f = new RandomAccessFile(line, "r");

            actual_length = (int) f.length();
            // We repurpose the offset value to store the padded length
            ie = new IndexEntry(line, padded_length, actual_length);

            entries.add(ie);
            
            total_length += actual_length;

            f.close();
        }
        br.close();


        table_size += entries.size() * entry_size;

        total_length += table_size;
        seq = new byte[total_length];   // Here we'll write the full file
        byte[] aux;

        int pointer_table = 0;
        int pointer_data = table_size;  // Data starts right after the table

        // Write the number of entries in the first 4 bytes of the table
        aux = int2bytes(entries.size());
        seq[0] = aux[0];
        seq[1] = aux[1];
        seq[2] = aux[2];
        seq[3] = aux[3];
        pointer_table = 4;

        // Write each of the files into seq and update its pointer in the table
        for (int i = 0; i < entries.size(); i++){
            // Update pointer
            aux = int2bytes(pointer_data);

            seq[pointer_table] = aux[0];
            seq[pointer_table + 1] = aux[1];
            seq[pointer_table + 2] = aux[2];
            seq[pointer_table + 3] = aux[3];

            pointer_table += 4;

            // Write the file into our byte sequence
            aux = new byte[entries.get(i).size];

            f = new RandomAccessFile(entries.get(i).name, "r");
            f.read(aux);
            f.close();

            System.out.println("P.Table: " + pointer_table + " - P.Data: " + pointer_data +
                    " - Length: " + aux.length + " - Total: " + total_length);

            for (int j = 0; j < aux.length; j++)
                seq[pointer_data + j] = aux[j];

            pointer_data += entries.get(i).size;
        }

        // Save the byte sequence to a file
        f = new RandomAccessFile(filename, "rw");
        f.write(seq);
        f.close();

        System.out.println("Finished. File " + filename + " built successfully.");
    }
}
