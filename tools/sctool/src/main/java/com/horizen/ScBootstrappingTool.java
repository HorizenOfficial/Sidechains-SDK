package com.horizen;
import java.util.Scanner;

public class ScBootstrappingTool {
    // TO DO: should become a separate .jar
    public static void main(String args[]) {
        MessagePrinter printer = new ConsolePrinter();
        CommandProcessor processor = new CommandProcessor(printer);
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
