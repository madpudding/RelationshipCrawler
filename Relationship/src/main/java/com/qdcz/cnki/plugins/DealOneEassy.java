package com.qdcz.cnki.plugins;

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
import org.neo4j.driver.v1.*;

import java.io.*;
import java.util.concurrent.Callable;

import static org.neo4j.driver.v1.Values.parameters;

/*
*
* 处理单篇论文的
* 获得论文标题、作者、作者导师、专业、学校、年份、学位
* */
public class DealOneEassy implements Callable {
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
                .setConnectionRequestTimeout(70000)
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

            //设置字节数
            byte[] data=new byte[1024];
            String content=inputStreamToString(httpResp.getEntity().getContent());
            data=content.getBytes();
            //获取文章标题
            String title=Tools.get_one_item(data,"UTF-8","//*[@id='chTitle']//text()");
            //获取作者
            String author=Tools.get_one_item(data,"UTF-8","//*[@class='summary pad10']/p[2]/a//text()");
            //获取作者导师
            String teacher=Tools.get_one_item(data,"UTF-8","//*[@class='summary pad10']/p[3]/a//text()");
            //获取单位基本信息
            String unit=Tools.get_one_item(data,"UTF-8","//*[@class='summary pad10']/p[4]/a//text()");
            //获取其它信息
            String message=Tools.get_one_item(data,"UTF-8","//*[@class='summary pad10']/p[4]//text()");
            //为空可能出现全英文标题
            if(title==null){
                title=Tools.get_one_item(data,"UTF-8","//*[@id='enTitle']//text()");
            }
            //为空则重新来
            if(author==null) {
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

                    //获取文章标题
                    title=Tools.get_one_item(data,"UTF-8","//*[@id='chTitle']//text()");
                    //获取作者
                    author=Tools.get_one_item(data,"UTF-8","//*[@class='summary pad10']/p[2]/a//text()");
                    //获取作者导师
                     teacher=Tools.get_one_item(data,"UTF-8","//*[@class='summary pad10']/p[3]/a//text()");
                    //获取单位基本信息
                     unit=Tools.get_one_item(data,"UTF-8","//*[@class='summary pad10']/p[4]/a//text()");
                     //获取其它信息
                    message=Tools.get_one_item(data,"UTF-8","//*[@class='summary pad10']/p[4]//text()");
                    //不为空，则获得数据
                    if (author!=null) {
                        break;
                    }
                    //重复5次，仍获取不到，放弃。
                    if (i > 4) {
                        System.out.println("五次请求后传过来地网页文本未获取到内容,遂放弃"+"---"+data.length);
                        break;
                    }
                }
            }
            if(title!=null){
                //判断标题是否含有逗号
                boolean b=title.indexOf(",") >= 0;
                boolean bc=title.contains("，");
                //消除标题中含有的逗号
                if(b==true){
                    title=title.replaceAll(",","");
                }
                if(bc==true){
                    title=title.replaceAll("，","");
                }
            }
            //写入文件
            FileWriter fw=null;
            File file=null;
            //判断单位是否为空
            if(unit==null){
                teacher=Tools.get_one_item(data,"UTF-8","//*[@class='summary pad10']/p[3]//text()");
                String[] mes=teacher.split("，");
                //写入库
                String authorR=author;
                String titleR=title.replaceAll(",","");
                String konwlegeR=mes[1].replaceAll("\\s*","");
                String teacherR="无导师";
                String schoolR=mes[0].replaceAll("\\s*","").replaceAll("【作者基本信息】","");
                String levelR=mes[3].replaceAll("\\s*","");
                String yearR=mes[2].replaceAll("\\s*","");
                Driver driver=GraphDatabase.driver("bolt://192.168.100.110:7687",
                        AuthTokens.basic("sjcjb","hpre&-*123"));
                try(Session session=driver.session()){
                    try(Transaction tx=session.beginTransaction()){
                        tx.run("create ("+authorR+":author{name:'"+authorR+"',level:'"+levelR+"',year:'"+yearR+"',eassy:'"+titleR+"'}),"+
                                "("+teacherR+":teacher{name:'"+teacherR+"'})," +
                                "("+konwlegeR+":konwledge{name:'"+konwlegeR+"'})," +
                                "("+schoolR+":school{name:'"+schoolR+"'}),"+
                                "("+titleR+":title{name:'"+titleR+"'}),"+
                                "("+authorR+")-[:WRITE]->("+titleR+")," +
                                "("+titleR+")-[:GUIDE]->("+teacherR+")," +
                                "("+authorR+")-[:BELONG]->("+konwlegeR+")," +
                                "("+konwlegeR+")-[:BELONG]->("+schoolR+")," +
                                "("+teacherR+")-[:BELONG]->("+schoolR+")");
                        tx.success();
                    }

                driver.close();
                }
            }else{
                //切割获取信息
                String[] messages=message.split("，");
                //写入库
                String authorR=author.replaceAll("[\\p{P}+~$`^=|<>～｀＄＾＋＝｜＜＞￥×]","").replaceAll(" ","");
                String titleR=title.replaceAll("→","").replaceAll("-","").replaceAll("[\\p{P}+~$`^=|<>～｀＄＾＋＝｜＜＞￥×]","");
                System.out.println(titleR);
                String konwlegeR=messages[1].replaceAll("\\s*","").replaceAll("[\\p{P}+~$`^=|<>～｀＄＾＋＝｜＜＞￥×]","");
                String teacherR=teacher.replaceAll("[\\p{P}+~$`^=|<>～｀＄＾＋＝｜＜＞￥×]","").replaceAll(" ","");
                String schoolR=unit.replaceAll("[\\p{P}+~$`^=|<>～｀＄＾＋＝｜＜＞￥×]","");
                String levelR=messages[3].replaceAll("\\s*","");
                String yearR=messages[2].replaceAll("\\s*","");
                Driver driver=GraphDatabase.driver("bolt://192.168.100.110:7687",
                        AuthTokens.basic("sjcjb","hpre&-*123"));
                try(Session session=driver.session()){
                    try(Transaction tx=session.beginTransaction()){
                        tx.run("create ("+authorR+":author{name:'"+authorR+"',level:'"+levelR+"',year:'"+yearR+"',eassy:'"+titleR+"'}),"+
                                        "("+teacherR+":teacher{name:'"+teacherR+"'})," +
                                        "("+konwlegeR+":konwledge{name:'"+konwlegeR+"'})," +
                                        "("+schoolR+":school{name:'"+schoolR+"'}),"+
                                        "("+titleR+":title{name:'"+titleR+"'}),"+
                                        "("+authorR+")-[:WRITE]->("+titleR+")," +
                                        "("+titleR+")-[:GUIDE]->("+teacherR+")," +
                                        "("+authorR+")-[:BELONG]->("+konwlegeR+")," +
                                        "("+konwlegeR+")-[:BELONG]->("+schoolR+")," +
                                        "("+teacherR+")-[:BELONG]->("+schoolR+")");
                        tx.success();
                    }

                    driver.close();
            }
            }
        }

        catch (Exception e) {
            System.out.println("出现异常！");
            System.out.println(e);
            e.printStackTrace();

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
        httpReq.setHeader("Accept", "ext/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
        httpReq.setHeader("Accept-Encoding", "gzip, deflate");
        httpReq.setHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        httpReq.setHeader("Connection","keep-alive");
        httpReq.setHeader("Host","gb.oversea.cnki.net");
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

    public DealOneEassy(String u){
        url=u;
    }

    /*
     * 程序入口
     * */
    @Override
    public String call() {
        //System.out.println(url);
        doGetRequest(url);
        String name="已完成";
        return name;
    }

}
