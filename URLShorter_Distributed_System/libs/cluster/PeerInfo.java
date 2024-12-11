package libs.cluster;

import libs.config.AppletType;

public class PeerInfo {
    public final String hostName;
    public final AppletType appletType;

    public PeerInfo(String hostName, AppletType appletType) {
        this.hostName = hostName;
        this.appletType = appletType;
    }

    @Override
    public String toString() {
        return "PeerInfo{" +
                "hostName='" + hostName + '\'' +
                ", appletType=" + appletType +
                '}';
    }
}
