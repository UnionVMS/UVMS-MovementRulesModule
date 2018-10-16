package eu.europa.ec.fisheries.uvms.movementrules.rest.service;

import eu.europa.ec.fisheries.schema.mobileterminal.types.v1.ComChannelAttribute;
import eu.europa.ec.fisheries.schema.mobileterminal.types.v1.ComChannelType;
import eu.europa.ec.fisheries.schema.mobileterminal.types.v1.MobileTerminalId;
import eu.europa.ec.fisheries.schema.mobileterminal.types.v1.MobileTerminalType;
import eu.europa.ec.fisheries.uvms.asset.client.model.AssetMTEnrichmentRequest;
import eu.europa.ec.fisheries.uvms.asset.client.model.AssetMTEnrichmentResponse;
import eu.europa.ec.fisheries.wsdl.asset.types.Asset;
import eu.europa.ec.fisheries.wsdl.asset.types.AssetHistoryId;
import eu.europa.ec.fisheries.wsdl.asset.types.AssetId;
import eu.europa.ec.fisheries.wsdl.asset.types.AssetIdType;

import javax.ejb.Stateless;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;

@Path("UnionVMS/asset/rest/internal")
@Stateless
public class AssetMTRestMock {


    @POST
    @Path("collectassetmt")
    @Consumes(value = {MediaType.APPLICATION_JSON})
    @Produces(value = {MediaType.APPLICATION_JSON})
    public Response collectAssetMT(AssetMTEnrichmentRequest request) {

        //request.get
        Asset asset = getBasicAsset();
        MobileTerminalType mt = getBasicMobileTerminalType();

        AssetMTEnrichmentResponse response = new AssetMTEnrichmentResponse();
        response = enrichementHelper(response, asset);
        response = enrichementHelper(request, response, mt);

        return Response.ok(response).build();
    }


    private Asset getBasicAsset() {
        Asset asset = new Asset();
        asset.setIrcs("IRCS");
        AssetId assetId = new AssetId();
        assetId.setType(AssetIdType.GUID);
        assetId.setGuid(UUID.randomUUID().toString());
        asset.setAssetId(assetId);
        AssetHistoryId assetHistoryId = new AssetHistoryId();
        assetHistoryId.setEventId(UUID.randomUUID().toString());
        asset.setEventHistory(assetHistoryId);
        asset.setName("Test Asset");
        asset.setCountryCode("SWE");
        return asset;
    }


    private static MobileTerminalType getBasicMobileTerminalType() {
        MobileTerminalType mobileTerminal = new MobileTerminalType();
        mobileTerminal.setConnectId(UUID.randomUUID().toString());
        MobileTerminalId mobileTerminalId = new MobileTerminalId();
        mobileTerminalId.setGuid(UUID.randomUUID().toString());
        mobileTerminal.setMobileTerminalId(mobileTerminalId);
        ComChannelType channel = new ComChannelType();
        ComChannelAttribute channelAttribute = new ComChannelAttribute();
        channelAttribute.setType("DNID");
        channelAttribute.setValue("TEST_DNID");
        channel.getAttributes().add(channelAttribute);
        ComChannelAttribute channelAttribute2 = new ComChannelAttribute();
        channelAttribute2.setType("MEMBER_NUMBER");
        channelAttribute2.setValue("TEST_MEMBER_NUMBER");
        channel.getAttributes().add(channelAttribute2);
        mobileTerminal.getChannels().add(channel);
        return mobileTerminal;
    }

    private AssetMTEnrichmentResponse enrichementHelper(AssetMTEnrichmentResponse resp, Asset asset) {
        Map<String, String> assetId = createAssetId(asset);
        resp.setAssetId(assetId);
        resp.setAssetUUID(UUID.fromString(asset.getAssetId().getGuid()));
        resp.setAssetName(asset.getName());
        resp.setAssetHistoryId(UUID.fromString(asset.getEventHistory().getEventId()));
        resp.setFlagstate(asset.getCountryCode());  //same as flag state?
        resp.setExternalMarking(asset.getExternalMarking());
        resp.setGearType(asset.getGearType());
        resp.setCfr(asset.getCfr());
        resp.setIrcs(asset.getIrcs());
        resp.setMmsi(asset.getMmsiNo());

        // resp.setAssetStatus(asset.get);


        return resp;
    }

    private AssetMTEnrichmentResponse enrichementHelper(AssetMTEnrichmentRequest req, AssetMTEnrichmentResponse resp, MobileTerminalType mobTerm) {

        // here we put into response data about mobiletreminal / channels etc etc
        String channelGuid = getChannelGuid(mobTerm, req);
        resp.setChannelGuid(channelGuid);
        if (mobTerm.getConnectId() != null) {
            UUID connectidUUID = null;
            try {
                connectidUUID = UUID.fromString(mobTerm.getConnectId());
            } catch (IllegalArgumentException e) {
                connectidUUID = null;
            }
            resp.setMobileTerminalConnectId(connectidUUID);
        }
        resp.setMobileTerminalType(mobTerm.getType());
        if(mobTerm.getMobileTerminalId() != null) {
            resp.setMobileTerminalGuid(mobTerm.getMobileTerminalId().getGuid());
        }
        resp.setMobileTerminalIsInactive(mobTerm.isInactive());

        if(mobTerm.getChannels() != null){
            List<ComChannelType> channelTypes = mobTerm.getChannels();
            for(ComChannelType channelType : channelTypes){
                if(!channelType.getGuid().equals(channelGuid)){
                    continue;
                }
                List<ComChannelAttribute> attributes = channelType.getAttributes();
                for(ComChannelAttribute attr : attributes){
                    String type = attr.getType();
                    String val = attr.getValue();
                    if (DNID.equals(type)) {
                        resp.setDNID(val);
                    }
                    if (MEMBER_NUMBER.equals(type)) {
                        resp.setMemberNumber(val);
                    }
                    if (SERIAL_NUMBER.equals(type)) {
                        resp.setSerialNumber(val);
                    }
                }
            }
        }

        return resp;
    }

    private static final String DNID = "DNID";
    private static final String MEMBER_NUMBER = "MEMBER_NUMBER";
    private static final String GUID = "GUID";
    private static final String IMO = "IMO";
    private static final String IRCS = "IRCS";
    private static final String MMSI = "MMSI";
    private static final String CFR = "CFR";
    private static final String GFCM = "GFCM";
    private static final String UVI = "UVI";
    private static final String ICCAT = "ICCAT";
    public static final String SERIAL_NUMBER = "SERIAL_NUMBER";


    private Map<String, String> createAssetId(Asset asset) {
        Map<String, String> assetId = new HashMap<>();

        if (asset.getCfr() != null && asset.getCfr().length() > 0) {
            assetId.put(CFR, asset.getCfr());
        }
        if (asset.getAssetId() != null) {
            assetId.put(GUID, asset.getAssetId().getGuid());
        }
        if (asset.getImo() != null && asset.getImo().length() > 0) {
            assetId.put(IMO, asset.getImo());
        }
        if (asset.getIrcs() != null && asset.getIrcs().length() > 0) {
            assetId.put(IRCS, asset.getIrcs());
        }
        if (asset.getMmsiNo() != null && asset.getMmsiNo().length() > 0) {
            assetId.put(MMSI, asset.getMmsiNo());
        }
        if (asset.getGfcm() != null && asset.getGfcm().length() > 0) {
            assetId.put(GFCM, asset.getGfcm());
        }
        if (asset.getUvi() != null && asset.getUvi().length() > 0) {
            assetId.put(UVI, asset.getUvi());
        }
        if (asset.getIccat() != null && asset.getIccat().length() > 0) {
            assetId.put(ICCAT, asset.getIccat());
        }
        return assetId;
    }

    private String getChannelGuid(MobileTerminalType mobileTerminal, AssetMTEnrichmentRequest request) {
        String dnid = "";
        String memberNumber = "";
        String channelGuid = "";

        dnid = request.getDnidValue();
        memberNumber = request.getMemberNumberValue();

        // Get the channel guid
        boolean correctDnid = false;
        boolean correctMemberNumber = false;
        List<ComChannelType> channels = mobileTerminal.getChannels();
        for (ComChannelType channel : channels) {

            List<ComChannelAttribute> attributes = channel.getAttributes();

            for (ComChannelAttribute attribute : attributes) {
                String type = attribute.getType();
                String value = attribute.getValue();

                if (DNID.equals(type)) {
                    correctDnid = value.equals(dnid);
                }
                if (MEMBER_NUMBER.equals(type)) {
                    correctMemberNumber = value.equals(memberNumber);
                }
            }

            if (correctDnid && correctMemberNumber) {
                channelGuid = channel.getGuid();
            }
        }
        return channelGuid;
    }
}
