package cx.arcane.managers.geoManager;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.InetAddress;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeoData {
    private InetAddress ip;
    private String country;
    private String city;
    private String region;
    private String postal;
    private Double latitude;
    private Double longitude;
    private Long asn;
    private String isp;
}