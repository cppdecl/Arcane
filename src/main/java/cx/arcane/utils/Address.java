package cx.arcane.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public final class Address {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String serialize(InetAddress address) {
        return address != null ? address.getHostAddress() : null;
    }

    public static InetAddress deserialize(String value) {
        if (value == null) return null;
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public static String serializeList(List<InetAddress> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }

        try {
            List<String> addresses = new ArrayList<>(list.size());
            for (InetAddress addr : list) {
                addresses.add(addr.getHostAddress());
            }
            return MAPPER.writeValueAsString(addresses);
        } catch (Exception e) {
            return "[]";
        }
    }

    public static List<InetAddress> deserializeList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            String[] addresses = MAPPER.readValue(json, String[].class);

            List<InetAddress> result = new ArrayList<>(addresses.length);
            for (String addr : addresses) {
                try {
                    result.add(InetAddress.getByName(addr));
                } catch (Exception ignored) {}
            }
            return result;

        } catch (Exception e) {
            return List.of();
        }
    }

}
