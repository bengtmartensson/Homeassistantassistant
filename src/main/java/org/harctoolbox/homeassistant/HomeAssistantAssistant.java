/*
Copyright (C) 2022, 2023 Bengt Martensson.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 3 of the License, or (at
your option) any later version.

This program is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License along with
this program. If not, see http://www.gnu.org/licenses/.
*/

package org.harctoolbox.homeassistant;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonStructure;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParser;

public final class HomeAssistantAssistant {

    private static final Logger logger = Logger.getLogger(HomeAssistantAssistant.class.getName());

    public static final String HOMEASSISTANT_DEFAULT_HOST = "homeassistant";
    public static final int HOMEASSISTANT_DEFAULT_PORT = 8123;

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final String AUTHORIZATION_NAME = "Authorization";
    private static final String BEARER_NAME = "Bearer";
    private static final String API_NAME = "api";
    private static final String DEFAULT_TOKEN_FILE = "token.txt";
    private static final String ENTITY_ID = "entity_id";
    private static final String HTTP = "http";
    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String STATES = "states";
    private static final String STATE = "state";
    private static final String SERVICES = "services";
    private static final String EVENTS = "events";
    private static final String CONFIG = "config";
    private static final String TOGGLE = "toggle";
    private static final String TURN_ON = "turn_on";
    private static final String TURN_OFF = "turn_off";
    private static final String REMOTE = "remote";
    private static final String SEND_COMMAND = "send_command";
    private static final String COMMAND = "command";
    private static final String HOMEASSISTANT = "homeassistant";
    private static final String SHELL_COMMAND = "shell_command";

    private static final Proxy proxy = Proxy.NO_PROXY;
    private static final JsonWriterFactory writerFactory;

    static {
        Map<String, Boolean> config = new HashMap<>(1);
        config.put(JsonGenerator.PRETTY_PRINTING, true);
        writerFactory = Json.createWriterFactory(config);
    }

    private static String readToken(String tokenFile) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(tokenFile));
        String tok = reader.readLine();
        logger.log(Level.INFO, "Homeassistant token successfully read from file {0}", tokenFile);
        return tok;
    }

    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    public static void main(String[] args) {
        CommandLineArgs commandLineArgs = new CommandLineArgs();
        JCommander argumentParser = new JCommander(commandLineArgs);
        argumentParser.setProgramName(HomeAssistantAssistant.class.getSimpleName());

        try {
            argumentParser.parse(args);
        } catch (ParameterException ex) {
            System.err.println(ex.getMessage());
            System.exit(1);
        }

        if (commandLineArgs.help) {
            argumentParser.usage();
            System.exit(0);
        }

        Boolean success = null;
        try {
            HomeAssistantAssistant ha = new HomeAssistantAssistant(commandLineArgs.hostname, commandLineArgs.port, commandLineArgs.token, commandLineArgs.verbose, commandLineArgs.data);

            if (commandLineArgs.parameters.isEmpty()) {
                JsonObject o = ha.getRoot();
                prettyPrint(System.out, o);
            } else {
                switch (commandLineArgs.parameters.get(0)) {
                    case CONFIG: {
                        JsonStructure o = ha.getConfig();
                        prettyPrint(System.out, o);
                    }
                    break;
                    case EVENTS: {
                        JsonStructure o = ha.getEvents();
                        prettyPrint(System.out, o);
                    }
                    break;
                    case SERVICES: {
                        if (commandLineArgs.parameters.size() == 1) {
                            JsonStructure array = ha.getServices();
                            prettyPrint(System.out, array);
                        } else {
                            ha.services(commandLineArgs.parameters.get(1), commandLineArgs.parameters.get(2), commandLineArgs.entity_id, commandLineArgs.data);
                        }
                    }
                    break;
                    case STATES:
                    case STATE:  {
                        JsonStructure thing = commandLineArgs.entity_id == null ? ha.getStates() : ha.getState(commandLineArgs.entity_id);
                        prettyPrint(System.out, thing);
                    }
                    break;
                    case TOGGLE: {
                        success = ha.toggle(commandLineArgs.entity_id);
                    } break;
                    case TURN_ON: {
                        success = ha.turnOn(commandLineArgs.entity_id);
                    } break;
                    case TURN_OFF: {
                        success = ha.turnOff(commandLineArgs.entity_id);
                    }
                    break;
                    case SHELL_COMMAND: {
                        success = ha.shellCommand(commandLineArgs.parameters.get(1));
                    }
                    break;
                    case REMOTE: {
                        success = ha.remote(commandLineArgs.entity_id, commandLineArgs.parameters.get(1));
                    }
                    break;
                    default:
                        System.err.println("Unknown command: " + commandLineArgs.parameters.get(0));
                }
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        if (success != null)
            System.err.println(success ? "Success" : "Failure");
    }

    private static String toPrettyString(JsonObject jsonObject) throws IOException {
        try (Writer writer = new StringWriter()) {
            writerFactory.createWriter(writer).write(jsonObject);
            return writer.toString();
        }
    }

    private static void prettyPrint(OutputStream outputStream, JsonStructure jsonObject) throws IOException {
        writerFactory.createWriter(outputStream).write(jsonObject);
        outputStream.write('\r');
        outputStream.write('\n');
    }

    private final String urlRoot;
    private final Map<String, String> headers;
    private final boolean verbose;
    private final List<String> postData;

    public HomeAssistantAssistant(String host, int port, String token, boolean verbose, List<String> postData) throws IOException {
        urlRoot = HTTP + "://" + host + ":" + port + "/" + API_NAME + "/";
        headers = new HashMap<>(2);
        this.postData = postData;
        String actualToken = token.charAt(0) != '@' ? token : readToken(token.substring(1));
        headers.put(AUTHORIZATION_NAME, BEARER_NAME + " " + actualToken);
        headers.put(CONTENT_TYPE, APPLICATION_JSON);
        this.verbose = verbose;
    }

    public HomeAssistantAssistant() throws IOException {
        this(HOMEASSISTANT_DEFAULT_HOST, HOMEASSISTANT_DEFAULT_PORT, readToken(DEFAULT_TOKEN_FILE), false, new ArrayList<String>(0));
    }

    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    private JsonParser getStuff(String path) throws MalformedURLException, IOException {
        String urlString = urlRoot + path;
        URL url = new URL(urlString);
        if (verbose)
            System.err.println("GET-ting URL " +  url.toString());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection(proxy);
        connection.setRequestMethod(GET);
        headers.entrySet().forEach(kvp -> {
            connection.setRequestProperty(kvp.getKey(), kvp.getValue());
        });
        int response = connection.getResponseCode();
        if (verbose)
            System.err.println("Response code = " + response);
        InputStream inputStream = connection.getInputStream();
        JsonParser parser = Json.createParser(inputStream);
        JsonParser.Event x = parser.next();
        return parser;
    }

    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    private boolean putStuff(String path, Map<String, String> postData) throws IOException {
        String urlString = urlRoot + path;
        URL url = new URL(urlString);
        if (verbose)
            System.err.println("POST to URL " + url.toString());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection(proxy);
        connection.setRequestMethod(POST);
        connection.setDoOutput(true);
        headers.entrySet().forEach(kvp -> {
            connection.setRequestProperty(kvp.getKey(), kvp.getValue());
        });
        JsonObjectBuilder builder = Json.createObjectBuilder();
        postData.entrySet().forEach(kvp -> {
            builder.add(kvp.getKey(), kvp.getValue());
        });
        JsonObject obj = builder.build();
        PrintStream outStream = new PrintStream(connection.getOutputStream());
        outStream.print(obj);
        if (verbose)
            System.err.println("POST data: " + obj.toString());
        int response = connection.getResponseCode();
        if (verbose)
            System.err.println("Response = " + response);
        return response == HttpURLConnection.HTTP_OK || response == HttpURLConnection.HTTP_CREATED;
    }

    private boolean putStuff(String path, String entity_id) throws IOException {
        Map<String, String> map = new HashMap<>(1);
        if (entity_id != null && ! entity_id.isEmpty())
            map.put(ENTITY_ID, entity_id);
        return putStuff(path, map);
    }

    private static Map<String, String> mkDictionary(String entity_id) throws IOException {
        Map<String, String> map = new HashMap<>(4);
        if (entity_id != null && ! entity_id.isEmpty())
            map.put(ENTITY_ID, entity_id);
        return map;
    }

    private Map<String, String> mkDictionary(String entity_id, List<String> data) throws IOException {
        Map<String, String> map = mkDictionary(entity_id);
        if (data != null)
            for (int i = 0; i < data.size(); i += 2)
                map.put(data.get(i), data.get(i+1));
        for (int i = 0; i < postData.size(); i += 2)
            map.put(postData.get(i), postData.get(i+1));
        return map;
    }

    public boolean services(String domain, String serviceName, String entityId, List<String> data) throws IOException {
        return putStuff(SERVICES + "/" + domain + "/" + serviceName, mkDictionary(entityId, data));
    }

    public boolean services(String domain, String serviceName, String entityId) throws IOException {
           return services(domain, serviceName, entityId, null);
    }

    private boolean services(String serviceName, String entityId) throws IOException {
        return services(HOMEASSISTANT, serviceName, entityId);
    }

    private boolean remote(String entity_id, String command) throws IOException {
        String ent_id = entity_id.contains(".") ? entity_id : REMOTE + "." + entity_id;
        List<String> data = new ArrayList<>(4);
        data.add(COMMAND);
        data.add(command);
        return services(REMOTE, SEND_COMMAND, ent_id, data);
    }


    public boolean toggle(String entityId) throws IOException {
        return services(TOGGLE, entityId);
    }

    public boolean turnOn(String entityId) throws IOException {
        return services(TURN_ON, entityId);
    }

    public boolean turnOff(String entityId) throws IOException {
        return services(TURN_OFF, entityId);
    }

    public boolean shellCommand(String commandName) throws IOException {
        return services(SHELL_COMMAND, commandName, null, null);
    }

    public JsonObject getRoot() throws IOException {
        return getObject("");
    }

    private JsonObject getObject(String str) throws IOException {
        return getStuff(str).getObject();
    }

    private JsonArray getArray(String str) throws IOException {
        return getStuff(str).getArray();
    }

    public JsonObject getConfig() throws IOException {
        return getObject(CONFIG);
    }

    public JsonArray getEvents() throws IOException {
        return getArray(EVENTS);
   }

    public JsonArray getServices() throws IOException {
        return getArray(SERVICES);
    }

    public JsonArray getStates() throws IOException {
        return getArray(STATES);
    }

    public JsonObject getState(String entity_id) throws IOException {
        return getObject(STATES + "/" + entity_id);
    }

    private final static class CommandLineArgs {

        @Parameter(names = {"-d", "--data"}, arity = 2, description = "Extra data (in pairs) to be PUT")
        private List<String> data = new ArrayList<>(4);

        @Parameter(names = {"-e", "--entity", "--entity_id"}, description = "entity_id name in homeassistant")
        private String entity_id = null;

        @Parameter(names = {"-?", "--help"}, description = "Print help message")
        private boolean help = false;

        @Parameter(names = {"-h", "--host", "--homeassistant"}, description = "Hostname of homeassistant host")
        private String hostname = HOMEASSISTANT_DEFAULT_HOST;

        @Parameter(names = {"-p", "--port"}, description = "Port number for homeassistant host")
        private int port = HOMEASSISTANT_DEFAULT_PORT;

        @Parameter(names = {"-t", "--token"},  description = "Token for homeassistant; prepend by @ to read from file")
        private String token = "@token.txt";

        @Parameter(names = {"-v", "-verbose"}, description = "Verbose execution")
        private boolean verbose = false;

        @Parameter
        @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
        private List<String> parameters = new ArrayList<>(4);
    }
}
