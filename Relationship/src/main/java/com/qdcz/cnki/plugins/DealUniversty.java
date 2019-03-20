package com.qdcz.cnki.plugins;



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

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * 该类通过post请求获取到所有大学的title和id
 * 通过正则表达式得到title和id
 * throws InterruptedException
 * 使用jackson-data-format-csv 写入csv文件
 * */
public class DealUniversty implements Callable {
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
        cm.setMaxTotal(20);
        cm.setDefaultMaxPerRoute(5);

        proxy = new HttpHost(proxyHost, proxyPort, "http");

        credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(proxyUser, proxyPass));

        reqConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(70000)
                .setConnectTimeout(90000)
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

    public static List<String> doRequest(HttpRequestBase httpReq)  {
        CloseableHttpResponse httpResp = null;

        String str="";

        //构建数组存储数据
        List<String> codeList = new ArrayList<>();
        //url
        List<String> netPost = new ArrayList<>();

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
            Thread.sleep(500);
            String resultData="";
            resultData=inputStreamToString(httpResp.getEntity().getContent());

            //获取code
            String patternCode = "baseid=\\w+";
            Pattern pCode = Pattern.compile(patternCode);
            Matcher mCode = pCode.matcher(resultData);
            //元素全匹配
            while (mCode.find()) {
                codeList.add(mCode.group());
            }

            //组装拼接每个大学的URL
            String rootUrl = "http://navi.cnki.net/knavi/PPaperDetail?pcode=CDMD&logo=";

            for (String i : codeList) {
                String urls = rootUrl + i.substring(7);
                netPost.add(urls);
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
        return netPost;
    }

    /**
     * 设置请求头
     *
     * @param httpReq
     */
    private static void setHeaders(HttpRequestBase httpReq) {
        httpReq.setHeader("Accept", "text/plain, */*; q=0.01");
        httpReq.setHeader("Accept-Encoding", "gzip, deflate");
        httpReq.setHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        httpReq.setHeader("Cache-Control","max-age=0");
        httpReq.setHeader("Connection", "keep-alive");
        httpReq.setHeader("Content-Type", "application/x-www-form-urlencoded");
        httpReq.setHeader("Host", "navi.cnki.net");
        httpReq.setHeader("Origin", "http://navi.cnki.net");
        httpReq.setHeader("Referer", "http://epub.cnki.net/KNS/brief/result.aspx?productcode=cmfd");
        httpReq.setHeader("Upgrade-Insecure-Requests", "1");
        httpReq.setHeader("User-Agent", "Mozilla/5.0 (iPad; CPU OS 11_0 like Mac OS X) AppleWebKit/604.1.34 (KHTML, like Gecko) Version/11.0 Mobile/15A5341f Safari/604.1");
        httpReq.setHeader(switchIpHeaderKey, switchIpHeaderVal);
    }

    public static List<String> doPostRequest(String url,Integer pageindex) {
        List<String> str=new ArrayList<>();
        try {
            // 要访问的目标页面
            HttpPost httpPost = new HttpPost(url);
            Double random = Math.random();
            // 设置表单参数
            List params = new ArrayList();
            params.add(new BasicNameValuePair("SearchStateJson", "{\"StateID\":\"\",\"Platfrom\":\"\",\"QueryTime\":\"\",\"Account\":\"knavi\",\"ClientToken\":\"\",\"Language\":\"\",\"CNode\":{\"PCode\":\"CDMD\",\"SMode\":\"\",\"OperateT\":\"\"},\"QNode\":{\"SelectT\":\"\",\"Select_Fields\":\"\",\"S_DBCodes\":\"\",\"QGroup\":[],\"OrderBy\":\"RT|\",\"GroupBy\":\"\",\"Additon\":\"\"}}"));//设置页编码
            params.add(new BasicNameValuePair("displaymode", "1"));
            params.add(new BasicNameValuePair("pageindex", pageindex.toString()));
            params.add(new BasicNameValuePair("pagecount","21"));
            params.add(new BasicNameValuePair("index","1"));
            params.add(new BasicNameValuePair("random", random.toString()));
            httpPost.setEntity(new UrlEncodedFormEntity(params, "utf-8"));
            str=doRequest(httpPost);


        } catch (Exception e) {
            e.printStackTrace();
        }
        return  str;
    }

    private String url;
    private Integer pageindex;

    public DealUniversty(String u,Integer p){
        url=u;
        pageindex=p;
    }



    @Override
    public List<String> call() {
        List<String> str=doPostRequest(url,pageindex);
        return str;
    }
}
