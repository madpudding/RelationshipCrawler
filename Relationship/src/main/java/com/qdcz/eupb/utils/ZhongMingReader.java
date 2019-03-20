package com.qdcz.eupb.utils;

import com.qdcz.spider.utils.Tools;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
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

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ZhongMingReader  implements Callable {
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
        cm.setDefaultMaxPerRoute(180);

        proxy = new HttpHost(proxyHost, proxyPort, "http");

        credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(proxyUser, proxyPass));
        //配置请求参数
        reqConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(60000)
                .setConnectTimeout(90000)
                .setSocketTimeout(90000)
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

    /*
     * 解析页面
     * */
    public static void doRequest(HttpRequestBase httpReq) {
        CloseableHttpResponse httpResp = null;
        Vector<String> urls=new Vector<>();
        try {
            //添加请求头
            setHeaders(httpReq);
            //添加请求参数
            httpReq.setConfig(reqConfig);

            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(cm)
                    .setDefaultCredentialsProvider(credsProvider)
                    .setRetryHandler(httpRequestRetryHandler)
                    .build();

            AuthCache authCache = new BasicAuthCache();
            authCache.put(proxy, new BasicScheme());

            HttpClientContext localContext = HttpClientContext.create();
            localContext.setAuthCache(authCache);

            httpResp = httpClient.execute(httpReq, localContext);

            int statusCode = httpResp.getStatusLine().getStatusCode();
            //设置字节数
            byte[] data=new byte[1024];
            String content=inputStreamToString(httpResp.getEntity().getContent());
            data=content.getBytes();
            //获取页面单位数量
            Tools.getMultiResultsByOneXpathPattern(data,"UTF-8","//*[@id='app']/div[1]/div/div[1]/p[1]/a/@href",urls);
            //为空则重新来
            if(urls.size()==0) {
                int i = 0;
                while (true) {
                    httpResp.close();

                    httpClient = HttpClients.custom()
                            .setConnectionManager(cm)
                            .setDefaultCredentialsProvider(credsProvider)
                            .build();

                    authCache = new BasicAuthCache();
                    authCache.put(proxy, new BasicScheme());

                    localContext = HttpClientContext.create();
                    localContext.setAuthCache(authCache);

                    i++;

                    httpResp = httpClient.execute(httpReq, localContext);
                    content=inputStreamToString(httpResp.getEntity().getContent());
                    data=content.getBytes();

                    //获取内容
                    Tools.getMultiResultsByOneXpathPattern(data, "UTF-8", "//*[@id='app']/div[1]/div/div[1]/p[1]/a/@href", urls);
                    //不为空，则获得数据
                    if (urls.size() >5) {
                        //System.out.println("列表页重新请求后得到结果url个数为："+"--"+urls.size());
                        break;
                    }
                    //重复5次，仍获取不到，放弃。
                    if (i > 4) {
                        System.out.println("五次请求后传过来地网页文本未获取到内容,遂放弃"+"---"+data.length);
                        break;
                    }
                }
            }
            FileWriter fw=null;
            File file=new File("D:/万方/baourl.txt");
            //循环传参
            for(String u:urls){
                System.out.println(u);
                fw=new FileWriter(file,true);
                fw.write("https://www.zhongmin.cn"+u+"\n");
                fw.flush();
                fw.close();
            }
        }
        catch (Exception e) {
            System.out.println("出现异常！");
            System.out.println(e);
            System.out.println(urls.size());
        }finally {
            if(httpResp!=null&&httpReq!=null){
                try {
                    httpResp.close();
                    httpReq.releaseConnection();
                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println(Thread.currentThread().getName()+","+System.currentTimeMillis());
                }
            }
        }
        //关闭过期连接
        cm.closeExpiredConnections();



    }

    /**
     * 设置请求头
     *
     * @param httpReq
     */
    private static void setHeaders(HttpRequestBase httpReq) {
        httpReq.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
        httpReq.setHeader("Accept-Encoding", "gzip, deflate,br");
        httpReq.setHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        httpReq.setHeader("Connection","keep-alive");
        httpReq.setHeader("Cache-Control","max-age=0");
        httpReq.setHeader("Host","www.zhongmin.cn");
        httpReq.setHeader("Upgrade-Insecure-Requests","1");
        httpReq.setHeader("User-Agent","Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/67.0.3396.99 Safari/537.36");
        httpReq.setHeader(switchIpHeaderKey, switchIpHeaderVal);
    }

    /*
     *  GET请求
     *  throws Exception
     *  */
    public static void doGetRequest(String url) {
        try {
            HttpGet httpGet = new HttpGet(url);
            doRequest(httpGet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String url;

    public ZhongMingReader(String u){
        url=u;
    }

    /*
     * 程序入口
     * */
    @Override
    public String call() {
        doGetRequest(url);
        String name="已完成";
        return name;
    }

    public static void main(String args[]){

        try {
            File file=new File("D:/万方/zhongmingurl.txt");
            String encoding="UTF-8";
            Long fileLength=file.length();
            byte[] filecontent=new byte[fileLength.intValue()];
            FileInputStream in=new FileInputStream(file);
            in.read(filecontent);
            in.close();
            String content=new String(filecontent,encoding);
            String[] url=content.split("\n");
            ExecutorService threadPool=Executors.newFixedThreadPool(5);
            Set<Future<String>> set=new HashSet<>();

            //遍历得到每个url
            String u="https://www.zhongmin.cn/company/product/slist";
            for (int i=0;i<64;i++) {
                ZhongMingReader db=new ZhongMingReader(u);
                Future<String> future=threadPool.submit(db);
                set.add(future);
            }

            for(Future<String> future:set){
                String nc=future.get();
                boolean t=future.isDone();
                if(t==true){
                    threadPool.shutdown();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
