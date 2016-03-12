//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* Dave Parker <david.parker@comlab.ox.ac.uk> (University of Oxford)
//	
//------------------------------------------------------------------------------
//	
//	This file is part of PRISM.
//	
//	PRISM is free software; you can redistribute it and/or modify
//	it under the terms of the GNU General Public License as published by
//	the Free Software Foundation; either version 2 of the License, or
//	(at your option) any later version.
//	
//	PRISM is distributed in the hope that it will be useful,
//	but WITHOUT ANY WARRANTY; without even the implied warranty of
//	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//	GNU General Public License for more details.
//	
//	You should have received a copy of the GNU General Public License
//	along with PRISM; if not, write to the Free Software Foundation,
//	Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//	
//==============================================================================

package prism;

import java.io.*;
import java.net.*;
import java.util.*;

import parser.ast.*;

/**
* Example class demonstrating how to control PRISM programmatically,
* i.e. through the "API" exposed by the class prism.Prism.
* (this now uses the newer version of the API, released after PRISM 4.0.3)
* Test like this:
* PRISM_MAINCLASS=prism.PrismTest bin/prism ../prism-examples/polling/poll2.sm ../prism-examples/polling/poll3.sm
*/
public class PrismPythonTalker
{
    private Prism prism;
    private ModulesFile currentModel;
    private ServerSocket server;
    String directory;
    String fileName;
    int socketPort;
    
    public PrismPythonTalker(int port, String workDir, String prismFile){
        try{
            PrismLog mainLog;
            
            //init socket
            socketPort=port;
            server = new ServerSocket(socketPort);
            System.out.println("PRISM server running on port " + socketPort);
            

            fileName=prismFile;
            directory=workDir;
                        
            // Init PRISM
            mainLog = new PrismFileLog("stdout");
            //mainLog = new PrismDevNullLog(); 
            prism = new Prism(mainLog, mainLog);
            prism.initialise();
        }
        catch (PrismException e) {
            System.out.println("Error: " + e.getMessage());
        }
        catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
        
    
    public Prism getPrism(){
        return prism;
    }
    
    public ModulesFile getCurrentModel(){
        return currentModel;
    }       
              
    public ServerSocket getServer(){
        return server;
    }
    
    public int getSocketPort(){
        return socketPort;
    } 
    

    public boolean loadPrismModelFile(){
        try{
            currentModel = prism.parseModelFile(new File(directory+fileName));
            prism.loadPRISMModel(currentModel);
            return true;
        }
         catch (FileNotFoundException e) {
            System.out.println("Error: " + e.getMessage());
            return false;
        }
        catch (PrismException e) {
            System.out.println("Error: " + e.getMessage());
            return false;
        }
    }
    
    
    public Result callPrism(String ltlString, boolean generatePolicy, boolean getStateVector, boolean partialSat)  {
        try {
            PropertiesFile prismSpec;
            Result result;        
            prism.setStoreVector(getStateVector);    
            if (partialSat){
                prism.setEngine(Prism.EXPLICIT);
                prism.getSettings().set(PrismSettings.PRISM_EXPORT_ADV, "DTMC");
                prism.getSettings().set(PrismSettings.PRISM_EXPORT_ADV_FILENAME,directory + "/adv.tra");
                prism.setExportProductStates(true);
                prism.setExportProductStatesFilename(directory  + "/prod.sta");
                prism.setExportTarget(true);
                prism.setExportTargetFilename(directory +  "/prod.lab");
                prism.getSettings().setExportPropAut(true);
                prism.getSettings().setExportPropAutType("txt");
                prism.getSettings().setExportPropAutFilename(directory + "/prod.aut");
            } else {
                if(generatePolicy){
                    prism.getSettings().set(PrismSettings.PRISM_EXPORT_ADV, "DTMC");
                    prism.getSettings().set(PrismSettings.PRISM_EXPORT_ADV_FILENAME,directory + "/adv.tra");
                    prism.setExportProductStates(true);
                    prism.setExportProductStatesFilename(directory  + "/prod.sta");
                    prism.setExportProductTrans(true);
                    prism.setExportProductTransFilename(directory + "/prod.tra");
                    prism.setExportTarget(true);
                    prism.setExportTargetFilename(directory +  "/prod.lab");
                    prism.getSettings().setExportPropAut(true);
                    prism.getSettings().setExportPropAutType("txt");
                    prism.getSettings().setExportPropAutFilename(directory + "/prod.aut");
                } else {
                    prism.getSettings().set(PrismSettings.PRISM_EXPORT_ADV, "None");               
                    prism.setExportProductStates(false);
                    prism.setExportProductTrans(false);
                    prism.setExportTarget(false);
                }
            }
            loadPrismModelFile();
            if (!partialSat) {
                prism.exportStatesToFile(Prism.EXPORT_PLAIN, new File(directory + "original.sta"));
            }
            prismSpec=prism.parsePropertiesString(currentModel, ltlString);
            result = prism.modelCheck(prismSpec, prismSpec.getPropertyObject(0));
            return result;
        }
        catch (PrismException e) {
            System.out.println("Error: " + e.getMessage());
            return null;
        }
        catch (FileNotFoundException e) {
            System.out.println("File not found Error: " + e.getMessage());
            return null;
        }
    }
    
    public static void main(String args[]) throws Exception {
        String command;
        List<String> commands=Arrays.asList(new String[] {"check", "plan", "get_vector", "shutdown", "partial_sat_plan"});
        String ack;        
        String toClient;
        String ltlString;   
        Socket client;
        PropertiesFile propertiesFile;
        Result result;

        PrismPythonTalker talker=new PrismPythonTalker(Integer.parseInt(args[0]), args[1], args[2]); 
        client = talker.server.accept();
        System.out.println("got connection on port" + talker.getSocketPort());  
        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
        PrintWriter out = new PrintWriter(client.getOutputStream(),true);
        boolean run = true;
        
        while(run) { 
            command = in.readLine();
            System.out.println("received: " + command); 
            if(command == null){
                client = talker.server.accept();
                System.out.println("got connection on port" + talker.getSocketPort());
            } else {
                if (!commands.contains(command)) {
                    System.out.println("Socket comm is unsynchronised! Trying to recover...");
                    continue;
                }
                if (command.equals("check")){
                    ltlString=in.readLine();
                    result=talker.callPrism(ltlString,false,false, false);
                    toClient = result.getResult().toString();
                    System.out.println("checked");
                    out.println(toClient);
                    continue;
                }
                if (command.equals("plan")){
                    ltlString=in.readLine();
                    result=talker.callPrism(ltlString,true, false, false);
                    toClient =  result.getResult().toString();
                    System.out.println("planned");
                    out.println(toClient);
                    continue;
                }
                if (command.equals("partial_sat_plan")){
                    ltlString=in.readLine();
                    result=talker.callPrism(ltlString,true, false, true);
                    toClient = result.getResult().toString();
                    System.out.println("partial sat policy generated");
                    out.println(toClient);
                    continue;
                }
                if (command.equals("get_vector")){
                    ltlString=in.readLine();
                    result=talker.callPrism(ltlString,false, true, false);
                    StateVector vect = result.getVector();
                    toClient="start";
                    out.println(toClient);
                    ack=in.readLine();
                    if(ack == "error"){
                        System.out.println("Socket error, continuing without outputting state vector");
                        continue;
                    }
                    if (vect != null) {
                        int n = vect.getSize();
                        for (int i = 0; i < n; i++) {
                            toClient=vect.getValue(i).toString();
                            out.println(toClient);
                            ack=in.readLine();
                            if(ack == "error"){
                                System.out.println("Socket error, continuing without outputting state vector");
                                continue;
                            }
                        }
                        vect.clear();
                    }
                    out.println("end");
                    continue;
                }
                if (command.equals("shutdown")){
                    run=false;
                    client.close();
                    talker.server.close();
                    talker.prism.closeDown();
                    continue;
                }
            }
        }       
        System.exit(0);
    }
}








                
