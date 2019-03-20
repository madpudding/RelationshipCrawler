package com.qdcz.eupb.plugins;


import com.qdcz.spider.utils.Tools;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.neo4j.driver.v1.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;

/*
 * 中国专利局post请求测试
 * httpclient
 * */


/*
* 中国专利局post请求
* throws Exception
* 传入专利页面数量
* 解析得到参数
* */
public class EupbPostNum implements Callable {
    // 代理服务器
    final static String proxyHost = "http-pro.abuyun.com";
    final static Integer proxyPort = 9010;

    // 代理隧道验证信息
    final static String proxyUser = "H10X69817796RM1P";
    final static String proxyPass = "4E8D11AE8FF5F103";

    // IP切换协议头
    final static String switchIpHeaderKey = "Proxy-Switch-Ip";
    final static String switchIpHeaderVal = "yes";

    private static PoolingHttpClientConnectionManager cm = null;
    private static HttpRequestRetryHandler httpRequestRetryHandler = null;
    private static HttpHost proxy = null;

    private static CredentialsProvider credsProvider = null;
    private static RequestConfig reqConfig = null;

    static {
        ConnectionSocketFactory plainsf = PlainConnectionSocketFactory.getSocketFactory();
        LayeredConnectionSocketFactory sslsf = SSLConnectionSocketFactory.getSocketFactory();

        Registry registry = RegistryBuilder.create()
                .register("http", plainsf)
                .register("https", sslsf)
                .build();

        cm = new PoolingHttpClientConnectionManager(registry);
        cm.setMaxTotal(400);
        cm.setDefaultMaxPerRoute(100);

        proxy = new HttpHost(proxyHost, proxyPort, "http");

        credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(proxyUser, proxyPass));

        reqConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(50000)
                .setConnectTimeout(50000)
                .setSocketTimeout(100000)
                .setExpectContinueEnabled(false)
                .setProxy(new HttpHost(proxyHost, proxyPort))
                .build();
    }

    //避免块编码过早结束，故用IO读取
    private static String inputStreamToString(InputStream is) {

        String line = "";
        StringBuilder total = new StringBuilder();

        BufferedReader rd = new BufferedReader(new InputStreamReader(is));

        try {
            while ((line = rd.readLine()) != null) {
                total.append(line);
            }
        } catch (IOException e) {

        }

        return total.toString();
    }

    public static void doRequest(HttpRequestBase httpReq)  {
        CloseableHttpResponse httpResp = null;


        Vector<String> invent=new Vector<>();//发明公布
        Vector<String> inventId=new Vector<>();//申请公布号
        Vector<String> applicateTime=new Vector<>();//申请公布日
        Vector<String> inventor=new Vector<>();//发明人
        Vector<String> applyMan=new Vector<>();//申请人
        Vector<String> address=new Vector<>();//地址
        Vector<String> applyId=new Vector<>();//申请号
        Vector<String> kindId=new Vector<>();//分类ID
        try {
            setHeaders(httpReq);

            httpReq.setConfig(reqConfig);

            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(cm)
                    .setDefaultCredentialsProvider(credsProvider)
                    .build();

            AuthCache authCache = new BasicAuthCache();
            authCache.put(proxy, new BasicScheme());

            HttpClientContext localContext = HttpClientContext.create();
            localContext.setAuthCache(authCache);

            httpResp = httpClient.execute(httpReq, localContext);

            String content=inputStreamToString(httpResp.getEntity().getContent());
            byte[] data=null;
            data=content.getBytes();

            //获取元素
            Tools.getMultiResultsByOneXpathPattern(data,"UTF-8","//*[@class='cp_linr']/h1//text()",invent);
            Tools.getMultiResultsByOneXpathPattern(data,"UTF-8","//*[@class='cp_linr']/ul/li[1]//text()",inventId);
            Tools.getMultiResultsByOneXpathPattern(data,"UTF-8","//*[@class='cp_linr']/ul/li[2]//text()",applicateTime);
            Tools.getMultiResultsByOneXpathPattern(data,"UTF-8","//*[@class='cp_linr']/ul/li[6]//text()",inventor);
            Tools.getMultiResultsByOneXpathPattern(data,"UTF-8","//*[@class='cp_linr']/ul/li[5]//text()",applyMan);
            Tools.getMultiResultsByOneXpathPattern(data,"UTF-8","//*[@class='cp_linr']/ul/li[3]//text()",applyId);
            Tools.getMultiResultsByOneXpathPattern(data,"UTF-8","//*[@class='cp_linr']/ul/li[8]//text()",address);
            Tools.getMultiResultsByOneXpathPattern(data,"UTF-8","//*[@class='cp_linr']/ul/li[9]//text()",kindId);

            //写入文件
            FileWriter fw=null;
            File file=new File("D:/eupb/eupb.txt");
            try {
                fw=new FileWriter(file,true);
                for(int i=0;i<invent.size();i++){
                    fw.write(invent.get(i).replaceAll("\\s*","").replaceAll("\\[发明公布\\]&nbsp;","").replaceAll("[`~!@#$^&*()=|{}':;',\\[\\].<>/?~！@#￥……&*（）——|{}【】‘；：”“'。，、？%+_]","")+","
                            +inventId.get(i).replace("申请公布号：","")+","+
                            applicateTime.get(i).replace("申请公布日：","").replaceAll("[`~!@#$^&*()=|{}':;',\\[\\].<>/?~！@#￥……&*（）——|{}【】‘；：”“'。，、？%+_]","")+","
                            +applyId.get(i).replace("申请号：","").replaceAll("[`~!@#$^&*()=|{}':;',\\[\\].<>/?~！@#￥……&*（）——|{}【】‘；：”“'。，、？%+_]","")+","
                            +address.get(i).replace("地址：","").replaceAll("[`~!@#$^&*()=|{}':;',\\[\\].<>/?~！@#￥……&*（）——|{}【】‘；：”“'。，、？%+_]","")+","
                            +inventor.get(i).replaceAll("&ensp;","").replaceAll("\\s*","").replace("发明人：","")+","
                            +applyMan.get(i).replaceAll("\\s*","").replaceAll("&ensp;","").replace("申请人：","").replaceAll("[`~!@#$^&*()=|{}':;',\\[\\].<>/?~！@#￥……&*（）——|{}【】‘；：”“'。，、？%+_]","")+"\n"
                    );
                    fw.flush();
                    fw.close();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            for(int i=0;i<invent.size();i++) {
                String title = invent.get(i).replaceAll("\\s*", "").replaceAll("\\[发明公布\\]&nbsp;", "").replaceAll("[`~!@#$^&*()=|{}':;',\\[\\].<>/?~！@#￥……&*（）——|{}【】‘；：”“'。，、？%+_]", "");
                String inId = inventId.get(i).replace("申请公布号：", "");
                String appTime = applicateTime.get(i).replace("申请公布日：", "").replaceAll("[`~!@#$^&*()=|{}':;',\\[\\].<>/?~！@#￥……&*（）——|{}【】‘；：”“'。，、？%+_]", "");
                String appId = applyId.get(i).replace("申请号：", "").replaceAll("[`~!@#$^&*()=|{}':;',\\[\\].<>/?~！@#￥……&*（）——|{}【】‘；：”“'。，、？%+_]", "");
                String addr = address.get(i).replace("地址：", "").replaceAll("[`~!@#$^&*()=|{}':;',\\[\\].<>/?~！@#￥……&*（）——|{}【】‘；：”“'。，、？%+_]", "");
                String inMan = inventor.get(i).replaceAll("&ensp;", "").replaceAll("\\s*", "").replace("发明人：", "");
                //String kiId=kindId.get(i).replaceAll("\\s*","").replaceAll("&ensp;","").replaceAll("&nbsp;","").replace("分类号：","").replaceAll("[`~!@#$^&*()=|{}':;',\\[\\].<>/?~！@#￥……&*（）——|{}【】‘；：”“'。，、？%+_]","");
                String man = applyMan.get(i).replaceAll("\\s*", "").replaceAll("&ensp;", "").replace("申请人：", "").replaceAll("[`~!@#$^&*()=|{}':;',\\[\\].<>/?~！@#￥……&*（）——|{}【】‘；：”“'。，、？%+_]", "");
                int len = man.length();
                String str = "";
                String reStr1 = "";
                String reStr2 = "";
                Driver driver = GraphDatabase.driver("bolt://192.168.100.110:7687",
                        AuthTokens.basic("sjcjb", "hpre&-*123"));
                System.out.println(inMan);
                //发明人为多个的情况下
                if (len > 3 && inMan.contains(";")) {
                    String[] applyMans = inMan.split(";");
                    int mlen = applyMans.length;
                    for (int x = 0; x < applyMans.length; x++) {
                        str = str + "(" + applyMans[x] + ":inventor{name:'" + applyMans[x] + "',title:'" + title + "',proposer:'" + man + "'}),";
                        reStr1 = reStr1 + "(" + applyMans[x] + ")-[:INVENT]->(" + title + "),";
                        reStr2 = reStr2 + "(" + applyMans[x] + ")-[:BELONG]->(" + man + "),";
                    }
                    try (Session session = driver.session()) {
                        try (Transaction tx = session.beginTransaction()) {
                            //,kin_id:'"+kiId+"'
                            tx.run("create " + str + "" +
                                    "(" + title + ":title{name:'" + title + "',appid:'" + appId + "',inid:'" + inId + "',apptime:'" + appTime + "})," +
                                    "(" + man + ":proposer{name:'" + man + "'})," +
                                    "(" + addr + ":address{name:'" + addr + "'})," +
                                    "" + reStr1 + "" +
                                    "" + reStr2 + "" +
                                    "(" + man + ")-[:APPLY]->(" + title + ")," +
                                    "(" + man + ")-[:LIVE]->(" + addr + ")");
                            tx.success();
                        }

                        driver.close();
                    }

                } else if (len < 4) {
                    try (Session session = driver.session()) {
                        try (Transaction tx = session.beginTransaction()) {
                            //,kin_id:'"+kiId+"'
                            tx.run("create (" + inMan + ":inventor{name:'" + inMan + "',title:'" + title + "',proposer:'" + man + "'})," +
                                    "(" + title + ":title{name:'" + title + "',appid:'" + appId + "',inid:'" + inId + "',apptime:'" + appTime + "})," +
                                    "(" + man + ":proposer{name:'" + man + "'})," +
                                    "(" + addr + ":address{name:'" + addr + "'})," +
                                    "(" + inMan + ")-[:INVENT]->(" + title + ")," +
                                    "(" + inMan + ")-[:BELONG]->(" + man + ")," +
                                    "(" + man + ")-[:APPLY]->(" + title + ")," +
                                    "(" + man + ")-[:LIVE]->(" + addr + ")");
                            tx.success();
                        }

                        driver.close();
                    }

                }
            }

        } catch (Exception e) {
            e.printStackTrace();

        } finally {
            try {
                if (httpResp != null&&httpReq!=null) {
                    httpResp.close();
                    httpReq.releaseConnection();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        //释放无用连接
        cm.closeExpiredConnections();
    }

    /**
     * 设置请求头
     *
     * @param httpReq
     */
    private static void setHeaders(HttpRequestBase httpReq) {
        httpReq.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
        httpReq.setHeader("Accept-Encoding", "gzip, deflate");
        httpReq.setHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        httpReq.setHeader("Cache-Control","max-age=0");
        httpReq.setHeader("Connection", "keep-alive");
        httpReq.setHeader("Content-Type", "application/x-www-form-urlencoded");
        //httpReq.setHeader("Cookie","_gscu_7281245=305857594fqr8l77; _va_ref=%5B%22%22%2C%22%22%2C1531812988%2C%22https%3A%2F%2Fwww.baidu.com%2Flink%3Furl%3DvMrSaKjpuC2R3aiFqnqgMXCRD3qxMi6A6FhxXoU3rfY_knhH6ahCxARhi8-7b61y%26ck%3D4234.13.172.200.149.453.234.291%26shh%3Dwww.baidu.com%26sht%3Dbaiduhome_pg%26wd%3D%26eqid%3Dc764349f0005110f000000065b4d9b38%22%5D; _gscu_1718069323=31812988nt4i9163; _gscu_2029180466=31812988mjeem514; _va_id=dba6a59a8370a662.1531812988.1.1531813267.1531812988.; keycookie=09934d21b0; expirecookie=1531980595; JSESSIONID=A5533CBBA4CBDD673280F297A573E551; WEB=20111130; Hm_lvt_06635991e58cd892f536626ef17b3348=1531550040,1531810588,1531972128,1531980154; _gscbrs_7281245=1; TY_SESSION_ID=ca8c3d97-b5db-4325-aac3-d28efab546b4; Hm_lpvt_06635991e58cd892f536626ef17b3348=1531980227; _gscs_7281245=31980154gs72or77|pv:6");
        httpReq.setHeader("Host", "epub.sipo.gov.cn");
        httpReq.setHeader("Origin", "http://epub.sipo.gov.cn");
        httpReq.setHeader("Referer", "http://epub.sipo.gov.cn/patentoutline.action");
        httpReq.setHeader("Upgrade-Insecure-Requests", "1");
        httpReq.setHeader("User-Agent", "Mozilla/5.0 (iPad; CPU OS 11_0 like Mac OS X) AppleWebKit/604.1.34 (KHTML, like Gecko) Version/11.0 Mobile/15A5341f Safari/604.1");
        httpReq.setHeader(switchIpHeaderKey, switchIpHeaderVal);
    }

    public static void doPostRequest(Integer num,String classify) {
        try {
            String url="http://epub.sipo.gov.cn/patentoutline.action";
            // 要访问的目标页面
            HttpPost httpPost = new HttpPost(url);
            // 设置表单参数
            List params = new ArrayList();
            params.add(new BasicNameValuePair("showType", "1"));
            params.add(new BasicNameValuePair("strSources", "pip"));
            params.add(new BasicNameValuePair("strWhere", "IC='"+classify+"%'"));
            params.add(new BasicNameValuePair("pageSize", "3"));//设置页编码
            params.add(new BasicNameValuePair("pageNow", num.toString()));
            httpPost.setEntity(new UrlEncodedFormEntity(params, "utf-8"));
            doRequest(httpPost);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Integer Num;

    private String Clssify;

    public EupbPostNum(Integer u,String cl){
        Num=u;
        Clssify=cl;
    }

    @Override
    public Integer call() {
        System.out.println(Num+"------"+Clssify);
        doPostRequest(Num,Clssify);
        int i=0;
        return i;
    }

}

