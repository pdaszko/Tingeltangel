/*
    Copyright (C) 2015   Martin Dames <martin@bastionbytes.de>
  
    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along
    with this program; if not, write to the Free Software Foundation, Inc.,
    51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
  
*/
package tingeltangel.core;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import tingeltangel.core.constants.ScriptFile;
import tingeltangel.core.scripting.Command;
import tingeltangel.core.scripting.Commands;
import tingeltangel.core.scripting.Instance;
import tingeltangel.core.scripting.SyntaxError;
import tingeltangel.core.scripting.Disassembler;

public class Script {

    private final Entry entry;
    private String code;
    private boolean kill = false;
    private LinkedList<Instance> script = null;
    private HashMap<String, Integer> instanceLabelsSI = null;
    private HashMap<Integer, Integer> instanceLabelsII = null;
    
    private int labelCounter = 0;
    
    public Script(String code, Entry entry) {
        this.entry = entry;
        this.code = code;
    }

    public Script(byte[] binary, Entry entry) throws SyntaxError {
        this.entry = entry;
        this.code = new Disassembler().disassemble(binary);
    }
    
    void changeMade() {
        entry.changeMade();
    }
    
    public void setCode(String code) {
        this.code = code;
        changeMade();
    }
    
    @Override
    public String toString() {
        return(code);
    }
    
    public int getSize(boolean calledFromScript) throws SyntaxError {
        int rc = 0;
        int size = 0;
        try {
            BufferedReader in = new BufferedReader(new StringReader(code));
            String row;
            while((row = in.readLine()) != null) {
                rc++;
                row = row.trim().toLowerCase();
                if((!row.isEmpty()) && (!row.startsWith(ScriptFile.COMMENT)) && (!row.startsWith(ScriptFile.COLON))) {
                    int p = row.indexOf(ScriptFile.SINGLE_SPACE);
                    String args = "";
                    if(p != -1) {
                        args = row.substring(p + 1).trim();
                        row = row.substring(0, p);
                    }
                    if(!row.startsWith(ScriptFile.COLON)) {
                        if(row.startsWith(ScriptFile.CALL + ScriptFile.SINGLE_SPACE)) {
                            // extract argument
                            //System.out.println(args);
                            try {
                                Script sub = entry.getBook().getEntryByID(Integer.parseInt(args)).getScript();
                                if(sub == null) {
                                    throw new SyntaxError("methode nicht gefunden (oid=" + args + ")");
                                }
                                size += sub.getSize(true);
                            } catch(NumberFormatException nfe) {
                                throw new SyntaxError("call benötigt als Argument eine OID");
                            }
                        } else if(row.equals(ScriptFile.RETURN) && calledFromScript) {
                            size += 4; // because return gets replaced by jmp command
                        } else {
                            size += Commands.getSize(row);
                        }
                    }
                }
            }
        } catch(IOException ioe) {
            throw new Error(ioe);
        } catch(SyntaxError se) {
            se.setRow(rc);
            se.setTingID(entry.getTingID());
            throw se;
        }
        return(size + 1); // +1 for the tail (0x00)
    }
    
    public boolean isSub() {
        try {
            BufferedReader in = new BufferedReader(new StringReader(code));
            String row;
            while((row = in.readLine()) != null) {
                if(row.trim().startsWith(ScriptFile.RETURN)) {
                    in.close();
                    return(true);
                }
            }
            in.close();
        } catch(IOException e) {
            throw new Error(e);
        }
        return(false);
    }
    
    
    public void kill() {
        kill = true;
    }
    
    public void execute() throws SyntaxError {
        execute(false);
    }
    
    private void execute(boolean subCall) throws SyntaxError {
        compile();
        if(!subCall) {
            entry.getBook().getEmulator().setLastOID(entry.getTingID());
        }
        int pc = 0;
        kill = false;
        while(!kill) {
            if(pc >= script.size()) {
                SyntaxError error = new SyntaxError("missing 'end' command");
                error.setTingID(entry.getTingID());
                error.setRow(-1);
                throw error;
            }
            Instance instance = script.get(pc);
            
            System.out.println(entry.getTingID() + ":" + pc + " " + instance.toString(entry.getBook().getEmulator()));
            
            if(instance.getCommand().getAsm().equals(ScriptFile.END)) {
                return;
            } else if(instance.getCommand().getAsm().equals(ScriptFile.CALL) || instance.getCommand().getAsm().equals(ScriptFile.CALLID)) {
                int oid = instance.getFirstArgument();
                entry.getBook().getEntryByID(oid).getScript().execute(true);
                pc++;
            } else if(instance.getCommand().getAsm().equals(ScriptFile.RETURN)) {
                return;
            } else {
                int action = instance.execute(entry.getBook().getEmulator());
                if(action == Instance.JUMP) {
                    pc = instanceLabelsII.get(instance.getLabel());
                } else {
                    pc++;
                }
            }
        }
    }
    
    public HashSet<Integer> getAllUsedRegisters() throws IOException, SyntaxError {
        BufferedReader in = new BufferedReader(new StringReader(mergeCodeOnCalls().toString()));
        HashSet<Integer> registers = new HashSet<Integer>();
        String row;
        while((row = in.readLine()) != null) {
            row = row.trim();
            if((!row.isEmpty()) && (!row.startsWith(ScriptFile.COMMENT))) {
                int p = row.indexOf(" ");
                if(p != -1) {
                    row = row.substring(p).trim();
                    String[] vals = row.split(",");
                    for(int i = 0; i < vals.length; i++) {
                        String val = vals[i].trim().toLowerCase();
                        if(val.startsWith("v")) {
                            val = val.substring(1);
                            registers.add(Integer.parseInt(val));
                        }
                    }
                }
            }
        }
        
        return(registers);
    }
    
    private String mergeCodeOnCalls() throws IOException, SyntaxError {
        String returnLabel = "return_" + (labelCounter++);
        StringBuilder mergedCode = new StringBuilder();
        BufferedReader in = new BufferedReader(new StringReader(code));
        String row;
        int rc = 0;
        while((row = in.readLine()) != null) {
            rc++;
            row = row.trim().toLowerCase();
            if((!row.isEmpty()) && (!row.startsWith(ScriptFile.COMMENT))) {
                if(row.startsWith(ScriptFile.CALL + ScriptFile.SINGLE_SPACE)) {
                    try {
                        int oid = Integer.parseInt(row.substring(ScriptFile.CALL.length()).trim());
                        String subCode = entry.getBook().getEntryByID(oid).getScript().mergeCodeOnCalls();
                        mergedCode.append(subCode);
                    } catch(NumberFormatException nfe) {
                        SyntaxError error = new SyntaxError("call needs a value as argument");
                        error.setRow(rc);
                        error.setTingID(entry.getTingID());
                        throw error;
                    }
                } else if(row.equals(ScriptFile.RETURN)) {
                    mergedCode.append("jmp ").append(returnLabel).append(ScriptFile.LB);
                } else {
                    mergedCode.append(row).append(ScriptFile.LB);
                }
            }
        }
        in.close();
        mergedCode.append(ScriptFile.COLON).append(returnLabel).append(ScriptFile.LB);
        return(mergedCode.toString());
    }
    
    public byte[] compile() throws SyntaxError {
        HashMap<String, Integer> labels = new HashMap<String, Integer>();
        instanceLabelsSI = new HashMap<String, Integer>();
        instanceLabelsII = new HashMap<Integer, Integer>();
        script = new LinkedList<Instance>();
        int rc = 0;
        try {
            
            String mergedCode = mergeCodeOnCalls();
            
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bout);
        
            // generate label map
            BufferedReader in = new BufferedReader(new StringReader(mergedCode.toString()));
            int position = 0;
            int instancePos = 0;
            String row;
            while((row = in.readLine()) != null) {
                rc++;
                row = row.trim().toLowerCase();
                if((!row.isEmpty()) && (!row.startsWith(ScriptFile.COMMENT))) {
                    if(row.startsWith(ScriptFile.COLON)) {
                        labels.put(row.substring(1).trim(), position);
                        instanceLabelsSI.put(row.substring(1).trim(), instancePos);
                    } else {
                        int p = row.indexOf(ScriptFile.SINGLE_SPACE);
                        String cmd = row;
                        if(p != -1) {
                            cmd = row.substring(0, p);
                        }
                        position += Commands.getSize(cmd);
                        instancePos++;
                    }
                }
            }
            in.close();
            
            
            // generate binary
            in = new BufferedReader(new StringReader(mergedCode));
            rc = 0;
            while((row = in.readLine()) != null) {
                rc++;
                row = row.trim().toLowerCase();
                if((!row.isEmpty()) && (!row.startsWith(ScriptFile.COMMENT)) && (!row.startsWith(ScriptFile.COLON))) {
                    
                    int p = row.indexOf("//");
                    if(p != -1) {
                        row = row.substring(0, p).trim();
                    }
                    
                    p = row.indexOf(ScriptFile.SINGLE_SPACE);
                    String cmd = row;
                    String args = null;
                    if(p != -1) {
                        cmd = row.substring(0, p).trim();
                        args = row.substring(p).trim();
                    }
                    Command command = null;
                    
                    
                    String arg1 = null;
                    String arg2 = null;
                    switch(Commands.getArguments(cmd)) {
                        case 0:
                            command = Commands.getCommand(cmd);
                            break;
                        case 1:
                            arg1 = args;
                            command = Commands.getCommand(cmd, arg1);
                            break;
                        case 2:
                            p = args.indexOf(",");
                            arg1 = args.substring(0, p).trim();
                            arg2 = args.substring(p + 1).trim();
                            command = Commands.getCommand(cmd, arg1, arg2);
                            break;
                    }
                    
                    Instance instance = new Instance(command);
                    if(command.firstArgumentIsLabel()) {
                        Integer label = labels.get(arg1);
                        if(label == null) {
                            throw new SyntaxError("unknown label: " + arg1);
                        }
                        instance.setLabel(label);
                        instanceLabelsII.put(instance.getLabel(), instanceLabelsSI.get(arg1));
                    } else {
                        if(Commands.getArguments(cmd) > 0) {
                            
                            instance.setFirstArgument(arg1);
                        }
                        if(Commands.getArguments(cmd) > 1) {
                            instance.setSecondArgument(arg2);
                        }
                    }
                    
                    instance.compile(out);
                    
                    script.add(instance);
                }
            }    
            in.close();
            
            out.write(0x00);
            
            out.flush();
            byte[] result = bout.toByteArray();
            out.close();
            return(result);
        } catch(IOException ioe) {
            throw new Error(ioe);
        } catch(SyntaxError se) {
            
            if(se.getRow() < 0) {
            
                se.setRow(rc);
                se.setTingID(entry.getTingID());
            }
            throw se;
        }
    }
}
