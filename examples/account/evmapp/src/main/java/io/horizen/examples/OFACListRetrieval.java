package io.horizen.examples;

import com.google.gson.Gson;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class OFACListRetrieval {
    private static final String FEATURE_TYPE_TEXT = "Digital Currency Address - ";
    private static final Map<String, String> NAMESPACE = Collections.singletonMap("sdn", "http://www.un.org/sanctions/1.0");
    private static final List<String> POSSIBLE_ASSETS = List.of("ETH");
    private static final List<String> OUTPUT_FORMATS = Arrays.asList("TXT", "JSON");

    public static void main(String[] args) {
        CommandLineArguments cmdArgs = parseArguments(args);

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        try {
            File file = new File(String.valueOf(cmdArgs.sdn));
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
//            Document doc = dBuilder.parse(file);
//            doc.getDocumentElement().normalize();


            // different lib
            File xmlFile = new File("/home/david/Desktop/Sidechains-SDK/examples/account/evmapp/src/main/java/io/horizen/examples/sdn_advanced.xml");
            Document doc2 = Jsoup.parse(xmlFile, "UTF-8", "", org.jsoup.parser.Parser.xmlParser());

            String asset = "ETH"; // Change this to the asset you want to extract the ID for
            String expression = "FeatureType:contains(Digital Currency Address - " + asset + ")";
            Element featureTypeNode = doc2.selectFirst(expression);

            //


//            //
//            // Create an XPathFactory and XPath object
//            XPathFactory xPathFactory = XPathFactory.newInstance();
//            XPath xPath = xPathFactory.newXPath();
//
//            // Define the namespace context
//            NamespaceContext namespaceContext = new NamespaceContext() {
//                @Override
//                public String getNamespaceURI(String prefix) {
//                    if (prefix.equals("ns")) {
//                        return "http://www.un.org/sanctions/1.0";
//                    }
//                    return null;
//                }
//
//                @Override
//                public String getPrefix(String namespaceURI) {
//                    return null;
//                }
//
//                @Override
//                public Iterator<String> getPrefixes(String namespaceURI) {
//                    return null;
//                }
//            };
//
//            // Set the namespace context
//            xPath.setNamespaceContext(namespaceContext);
//
//            // Define the XPath expression to find the FeatureType node
//            String asset = "ETH"; // Change this to the asset you want to extract the ID for
//            //String expression = String.format("//sdn:FeatureType[.='%s']", "Digital Currency Address - " + asset);
//            //String expression = "sdn:ReferenceValueSets/sdn:FeatureTypeValues/sdn:FeatureType[.='Digital Currency Address - ETH']";
//            String expression = String.format("/ns:Sanctions/ns:ReferenceValueSets/ns:FeatureTypeValues/ns:FeatureType[.='%s']", "Digital Currency Address - " + asset);
//
//            // Compile the XPath expression and evaluate it
//            XPathExpression xPathExpression = xPath.compile(expression);
//            Node featureTypeNode = (Node) xPathExpression.evaluate(doc, XPathConstants.NODE);
//
//            int a = 4;
//            //
//
//            for (String asset2 : cmdArgs.assets) {
//                String addressId = getAddressId(doc, asset);
//                List<String> addresses = getSanctionedAddresses(doc, addressId);
//
//                addresses = new ArrayList<>(new HashSet<>(addresses)); // Deduplicate addresses
//                addresses.sort(Comparator.naturalOrder()); // Sort addresses
//
//                writeAddresses(addresses, asset, cmdArgs.format, cmdArgs.outpath);
//            }
        } catch (ParserConfigurationException | IOException e) {
            e.printStackTrace();
        }
    }

    private static CommandLineArguments parseArguments(String[] args) {
        CommandLineArguments cmdArgs = new CommandLineArguments();
        cmdArgs.assets = new ArrayList<>();
        cmdArgs.format = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-f") || args[i].equals("--output-format")) {
                while (++i < args.length && !args[i].startsWith("-")) {
                    cmdArgs.format.add(args[i]);
                }
                i--;
            } else if (args[i].equals("-path") || args[i].equals("--output-path")) {
                cmdArgs.outpath = Paths.get(args[++i]);
            } else {
                cmdArgs.assets.add(args[i]);
            }
        }

        if (cmdArgs.assets.isEmpty()) {
            cmdArgs.assets.add(POSSIBLE_ASSETS.get(0));
        }
        if (cmdArgs.format.isEmpty()) {
            cmdArgs.format.add(OUTPUT_FORMATS.get(0));
        }
        if (cmdArgs.outpath == null) {
            cmdArgs.outpath = Paths.get("./");
        }
        if (cmdArgs.sdn == null)
            cmdArgs.sdn = Path.of("/home/david/Desktop/Sidechains-SDK/examples/account/evmapp/src/main/java/io/horizen/examples/sdn_advanced.xml");

        return cmdArgs;
    }

    private static String getAddressId(Document doc, String asset) throws XPathExpressionException {
        XPathFactory xPathFactory = XPathFactory.newInstance();
        XPath xPath = xPathFactory.newXPath();
        String expression = String.format("sdn:ReferenceValueSets/sdn:FeatureTypeValues/*[.='%s']", FEATURE_TYPE_TEXT + asset);
        XPathExpression xPathExpression = xPath.compile(expression);
        Node featureTypeNode = (Node) xPathExpression.evaluate(doc, XPathConstants.NODE);

//        if (featureTypeNode == null) {
//            throw new RuntimeException("No FeatureType with the name " + FEATURE_TYPE_TEXT + asset + " found");
//        }
//
//        return featureTypeNode.getAttributes().getNamedItem("ID").getNodeValue();

        return "345";
    }

    private static List<String> getSanctionedAddresses(Document doc, String addressId) throws XPathExpressionException {
        XPathFactory xPathFactory = XPathFactory.newInstance();
        XPath xPath = xPathFactory.newXPath();
        String expression = String.format("sdn:DistinctParties//*[@FeatureTypeID='%s']", addressId);
        XPathExpression xPathExpression = xPath.compile(expression);

        NodeList versionDetailNodes = (NodeList) xPathExpression.evaluate(doc, XPathConstants.NODESET);
        List<String> addresses = new ArrayList<>();

        for (int i = 0; i < versionDetailNodes.getLength(); i++) {
            Node versionDetailNode = versionDetailNodes.item(i);
            addresses.add(versionDetailNode.getTextContent());
        }

        return addresses;
    }

    private static void writeAddresses(List<String> addresses, String asset, List<String> formats, Path outpath) {
        if (formats.contains("TXT")) {
            writeAddressesTxt(addresses, asset, outpath);
        }
        if (formats.contains("JSON")) {
            writeAddressesJson(addresses, asset, outpath);
        }
    }

    private static void writeAddressesTxt(List<String> addresses, String asset, Path outpath) {
        Path txtFilePath = outpath.resolve("sanctioned_addresses_" + asset + ".txt");
        try (PrintWriter writer = new PrintWriter(txtFilePath.toFile())) {
            for (String address : addresses) {
                writer.println(address);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeAddressesJson(List<String> addresses, String asset, Path outpath) {
        Path jsonFilePath = outpath.resolve("sanctioned_addresses_" + asset + ".json");
        try (FileWriter writer = new FileWriter(jsonFilePath.toFile())) {
            writer.write(new Gson().toJson(addresses));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class CommandLineArguments {
        List<String> assets;
        Path sdn;
        List<String> format;
        Path outpath;
    }
}
