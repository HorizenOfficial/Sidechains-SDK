package com.horizen;
import java.util.Arrays;
import java.util.Scanner;

public class ScBootstrappingTool {
    public static void main(String args[]) {
        MessagePrinter printer = new ConsolePrinter();
        CommandProcessor processor = new CommandProcessor(printer);
        if(args.length > 0)
            try {
                StringBuilder cmd = new StringBuilder(args[0]);
                for(int i=1; i<args.length; i++)
                    cmd.append(" ").append(args[i]);
                processor.processCommand(cmd.toString());
            }catch (Exception e){
                printer.print(e.getMessage());
            }
        else{
            printer.print("Tool successfully started...\nPlease, enter the command:");
            Scanner scanner = new Scanner(System.in);
            while(true) {
                String input = scanner.nextLine();
                try {
                    if(input.startsWith("exit"))
                        break;
                    processor.processCommand(input);
                }
                catch(Exception e) {
                    printer.print(e.getMessage());
                }
            }
        }
    }
}
