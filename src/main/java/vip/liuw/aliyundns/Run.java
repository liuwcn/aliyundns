package vip.liuw.aliyundns;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.alidns.model.v20150109.DescribeDomainRecordsRequest;
import com.aliyuncs.alidns.model.v20150109.DescribeDomainRecordsResponse;
import com.aliyuncs.alidns.model.v20150109.DescribeDomainRecordsResponse.Record;
import com.aliyuncs.alidns.model.v20150109.UpdateDomainRecordRequest;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import vip.liuw.utils.CheckUtils;
import vip.liuw.utils.FileUtils;
import vip.liuw.utils.IPUtils;
import vip.liuw.utils.logger.Logger;
import vip.liuw.utils.logger.LoggerFactory;
import vip.liuw.utils.properties.PropertiesUtils;
import vip.liuw.utils.properties.PropertiesWrapper;

import java.util.Iterator;
import java.util.List;

public class Run {
    private static IAcsClient client = null;
    private static PropertiesWrapper wrapper;
    private static Logger log = LoggerFactory.getLogger("main");

    public static void main(String[] args) {
        config();
        String domains = wrapper.getProperty("domains");
        String lastIp = wrapper.getProperty("lastIp");
        if (CheckUtils.isNull(domains)) {
            log.debug("no damains was found");
        } else {
            String currentIp = IPUtils.getIP();
            if (CheckUtils.isNull(currentIp)) {
                log.debug("Outside the network through a web query by IP failed");
            } else {
                log.debug("current ip:{}", currentIp);
                if (currentIp.equals(lastIp)) {
                    log.debug("ip does not change");
                } else {
                    String[] domainArray = domains.split(";");
                    //第个域名都解析
                    for (int i = 0; i < domainArray.length; ++i) {
                        String domain = domainArray[i];
                        DescribeDomainRecordsRequest request = new DescribeDomainRecordsRequest();
                        String mainDomain;
                        String secondDomain;
                        String[] arr = domain.split("\\.");
                        //一级域名
                        if (arr.length == 2) {
                            mainDomain = domain;
                            secondDomain = "@";
                        } else {//二级域名
                            if (arr.length != 3) {
                                return;
                            }

                            mainDomain = arr[1] + "." + arr[2];
                            secondDomain = arr[0];
                        }

                        request.setDomainName(mainDomain);
                        DescribeDomainRecordsResponse response;

                        try {
                            response = client.getAcsResponse(request);
                        } catch (ClientException e) {
                            e.printStackTrace();
                            return;
                        }

                        List<Record> list = response.getDomainRecords();
                        Iterator iterator = list.iterator();

                        while (iterator.hasNext()) {
                            Record record = (Record) iterator.next();
                            if (record.getRR().equals(secondDomain)) {
                                UpdateDomainRecordRequest updateRequest = new UpdateDomainRecordRequest();
                                updateRequest.setRR(record.getRR());
                                updateRequest.setRecordId(record.getRecordId());
                                updateRequest.setType(record.getType());
                                updateRequest.setValue(currentIp);

                                try {
                                    client.getAcsResponse(updateRequest);
                                    wrapper.store("lastIp", currentIp);
                                    log.debug("UpdateDomainRecordResponse success");
                                } catch (ClientException e) {
                                    log.debug("UpdateDomainRecordResponse failed:{}", e);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    //读取变量
    private static void config() {
        wrapper = PropertiesUtils.load(FileUtils.getClassPathFile("config.properties"));
        String regionId = wrapper.getProperty("regionId");
        String accessKeyId = wrapper.getProperty("accessKeyId");
        String accessKeySecret = wrapper.getProperty("accessKeySecret");
        IClientProfile profile = DefaultProfile.getProfile(regionId, accessKeyId, accessKeySecret);
        client = new DefaultAcsClient(profile);
    }
}
