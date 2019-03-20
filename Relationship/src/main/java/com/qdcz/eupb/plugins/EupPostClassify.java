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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/*
 * 中国专利局post请求测试
 * httpclient
 * */


/*
 * 中国专利局post请求
 * throws Exception
 * 传入分类号
 * 得到分类号专利数量
 * */
public class EupPostClassify implements Callable {
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
        cm.setMaxTotal(200);
        cm.setDefaultMaxPerRoute(50);

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

    public static Integer  doRequest(HttpRequestBase httpReq)  {
        CloseableHttpResponse httpResp = null;

        int i=0;

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

            byte[] data=null;
            data=inputStreamToString(httpResp.getEntity().getContent()).getBytes();
            String btn=Tools.get_one_item(data,"UTF-8","//*[@class='next']/a[7]//text()");

            //若为空则重试
            if(btn==null){
                int j=0;
                while (true){
                    j++;
                    httpResp.close();
                    httpClient = HttpClients.custom()
                            .setConnectionManager(cm)
                            .setDefaultCredentialsProvider(credsProvider)
                            .build();

                    authCache = new BasicAuthCache();
                    authCache.put(proxy, new BasicScheme());

                    localContext = HttpClientContext.create();
                    localContext.setAuthCache(authCache);

                    httpResp = httpClient.execute(httpReq, localContext);

                    data=null;
                    data=inputStreamToString(httpResp.getEntity().getContent()).getBytes();
                    btn=Tools.get_one_item(data,"UTF-8","//*[@class='next']/a[7]//text()");

                    if(btn!=null){
                        break;
                    }

                    if(j>9){
                        System.out.println("分类页重复十次放弃");
                        break;
                    }
                }
            }
            System.out.println(btn);
            i=Integer.valueOf(btn).intValue();
            //System.out.println(i);
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

        return i;
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
        httpReq.setHeader("Host", "epub.sipo.gov.cn");
        httpReq.setHeader("Origin", "http://epub.sipo.gov.cn");
        httpReq.setHeader("Referer", "http://epub.sipo.gov.cn/patentoutline.action");
        httpReq.setHeader("Upgrade-Insecure-Requests", "1");
        httpReq.setHeader("User-Agent", "Mozilla/5.0 (iPad; CPU OS 11_0 like Mac OS X) AppleWebKit/604.1.34 (KHTML, like Gecko) Version/11.0 Mobile/15A5341f Safari/604.1");
        httpReq.setHeader(switchIpHeaderKey, switchIpHeaderVal);
    }

    public static Map<Integer,String> doPostRequest(String classify) {
        int  i=0;
        Map<Integer,String> map=new HashMap<>();
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
            params.add(new BasicNameValuePair("pageNow", "1"));
            httpPost.setEntity(new UrlEncodedFormEntity(params, "utf-8"));
            i=doRequest(httpPost);
            map.put(i,classify);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return  map;
    }

    private String classify;

    public EupPostClassify(String cl){
        classify=cl;
    }

    @Override
    public Map<Integer,String> call() {
        Map<Integer,String> map=new HashMap<>();
        map=doPostRequest(classify);
        return map;
    }

}
