package com.qdcz.wanfang.plugin;

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
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;

import java.io.*;
import java.util.Vector;
import java.util.concurrent.Callable;


/*
 * 解析一篇文章的页面
 * 获取参数
 * throws Exception
 * */
public class DealOnePage implements Callable {
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
        cm.setMaxTotal(600);
        cm.setDefaultMaxPerRoute(200);

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
        } catch (Exception e) {

        }

        if(is!=null){
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println(e);
            }
        }

        return total.toString();
    }

    /*
     * 解析页面
     * */
    public static void doRequest(HttpRequestBase httpReq) {
        CloseableHttpResponse httpResp = null;

        Vector<String> unit = new Vector<>();//单位数量
        Vector<String> authors = new Vector<>();//作者数量
        Vector<String> spans = new Vector<>();//作者span所对应的单位item

        String title=null;//文章标题

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

            int statusCode = httpResp.getStatusLine().getStatusCode();

                //设置字节数
                byte[] data = null;
                String content = inputStreamToString(httpResp.getEntity().getContent());
                data = content.getBytes();
                //获取页面单位数量
                Tools.getMultiResultsByOneXpathPattern(data, "UTF-8", "//*[@id='perildical_dl']/tbody/tr[3]/td[1]/ol/li//text()", unit);
                //获取期刊文章标题
                String tt = Tools.get_one_item(data, "UTF-8", "//*[@id='title0']//text()");

                if(tt!=null){
                     title = tt.replaceAll("\\s*", "");
                }
                //获取出版时间
                String ti = Tools.get_one_item(data, "UTF-8", "//*[@id='wfpublishdate']/td//text()");
                String time =null;
                if(ti!=null){
                    time = ti.replaceAll("\\s*", "");
                }

                //获取刊名
                String magzine = Tools.get_one_item(data, "UTF-8", "//*[@id='perildical_dl']/tbody/tr[4]/td/a//text()");



                //为空再次请求
                if (tt==null||ti==null||unit.size()<1||magzine==null) {
                    //System.out.println("文章页："+statusCode);
                    int k = 0;
                    while (true) {
                        httpResp.close();
                        k++;
                        //重新请求
                        httpClient = HttpClients.custom()
                                .setConnectionManager(cm)
                                .setDefaultCredentialsProvider(credsProvider)
                                .build();

                        authCache = new BasicAuthCache();
                        authCache.put(proxy, new BasicScheme());

                        localContext = HttpClientContext.create();
                        localContext.setAuthCache(authCache);

                        httpResp = httpClient.execute(httpReq, localContext);
                        content = inputStreamToString(httpResp.getEntity().getContent());
                        data = content.getBytes();

                        //获取页面单位数量,小于1则操作
                        if(unit.size()<1){
                            Tools.getMultiResultsByOneXpathPattern(data, "UTF-8", "//*[@id='perildical_dl']/tbody/tr[3]/td//text()", unit);
                            if(unit.size()<1){
                                Tools.getMultiResultsByOneXpathPattern(data, "UTF-8", "//*[@id='perildical_dl']/tbody/tr[3]/td[1]/ol/li//text()", unit);
                            }
                        }

                        //获取刊名
                        if(magzine==null){
                            magzine = Tools.get_one_item(data, "UTF-8", "//*[@id='perildical_dl']/tbody/tr[3]/td/a//text()");
                            if(magzine==null){
                                magzine = Tools.get_one_item(data, "UTF-8", "//*[@id='perildical_dl']/tbody/tr[4]/td/a//text()");
                            }
                        }

                        //获取期刊文章标题，不为空则操作
                        if(tt==null){
                            tt = Tools.get_one_item(data, "UTF-8", "//*[@id='title0']//text()");
                            if(tt!=null){
                                title = tt.replaceAll("\\s*", "");
                            }
                        }

                        //获取出版时间，不为空则操作
                        if(ti==null){
                            ti = Tools.get_one_item(data, "UTF-8", "//*[@id='wfpublishdate']/td//text()");
                            if(ti!=null){
                                time = ti.replaceAll("\\s*", "");
                            }
                        }

                        //获取作者
                        if(authors.size()<1){

                        }

                        //都不为空则跳出
                        if (unit.size()>0&&tt!=null&&ti!=null&&magzine!=null) {
                            //System.out.println("文章页重新请求后单位数量为："+unit.size());
                            break;
                        }

                        //超过五次，放弃
                        if (k > 5) {
                            System.out.println("文章页已达五次遂放弃");
                            break;
                        }

                    }

                }

                //一条记录
                String record=null;
                //若小于二则只有一个单位，不必多加判断
                if (unit.size() < 2) {
                    //获取作者数量
                    Tools.getMultiResultsByOneXpathPattern(data, "UTF-8", "//*[@class='author_td']/a//text()", authors);
                    StringBuffer sb = new StringBuffer();
                    //循环写入
                    for (int i = 0; i < authors.size(); i++) {
                        if(unit.size()!=0){
                            sb.append(authors.get(i).replaceAll("\\s*", "") + " " + unit.get(0).replaceAll("\\s*", "").replaceAll(",", " ") + ";");
                        }
                    }
                    //最终数据
                    if(magzine==null&&title==null&&time==null){
                        record=null;
                    }
                    else{
                        Driver driver=GraphDatabase.driver("bolt://192.168.100.110:7687",
                                AuthTokens.basic("sjcjb","hpre&-*123"));
                        record = magzine + "," + title + "," + sb/*.substring(0,sb.length()-1)*/ + "," + time + "\n";
                    }
                } else {
                    //获取作者数量
                    Tools.getMultiResultsByOneXpathPattern(data, "UTF-8", "//*[@class='author_td']/a//text()", authors);
                    //获取span数量
                    Tools.getMultiResultsByOneXpathPattern(data, "UTF-8", "//*[@class='author_td']/sup//text()", spans);
                    //绑定作者和单位
                    Vector<String> tu = new Vector<>();

                    //去除为空的span item
                    for(int l=0;l<(spans.size());l++){
                        if(spans.get(l).equals("")){
                            //按规律未添加item的一般为第一家单位
                            spans.set(l,spans.get(0));
                        }
                    }

                    //循环得到具体的作者单位
                    String span=null;//span item
                    for (int j = 0; j < authors.size(); j++) {
                        if(spans.size()!=0){
                            span = spans.get(j).replaceAll("\\[", "").replaceAll("\\]", "");
                            Object obj=Integer.valueOf(span).intValue(); //判断span是否为单纯的数字
                            //一人一家单位
                            if(obj instanceof Integer){
                                int s = Integer.parseInt(span);
                                tu.add(authors.get(j).replaceAll("\\s*", "") + "\t" + unit.get(s - 1).replaceAll("\\s*", "").replaceAll(",", "\t") + ";");
                            }else{
                                System.out.println(span);
                                //一人两家单位，打死也不信有三家
                                String spanTwo=span.replaceAll(",","");
                                int s1=Integer.parseInt(spanTwo.substring(0,1));
                                int s2=Integer.parseInt(spanTwo.substring(1));
                                tu.add(authors.get(j).replaceAll("\\s*", "") + "\t" + unit.get(s1 - 1).replaceAll("\\s*", "").replaceAll(",", "\t") + "\t"+unit.get(s2 - 1).replaceAll("\\s*", "").replaceAll(",", "\t") +";");
                            }
                        }

                    }
                    //循环添加到缓冲中
                    StringBuffer sb = new StringBuffer();
                    for (int k = 0; k < tu.size(); k++) {
                        sb.append(tu.get(k));
                    }
                    //最终数据
                     if(magzine==null&&title==null&&time==null){
                        record=null;
                     }
                     else{
                         record = magzine + "," + title + "," + sb/*.substring(0,sb.length()-1)*/ + "," + time + "\n";
                     }
                }

                //写入文件
                FileWriter fo = null;
                File file = new File("D:/万方/data.txt");
                fo = new FileWriter(file, true);
                synchronized (""){
                    if(record!=null){
                        fo.write(record);
                    }
                }
                fo.flush();
                fo.close();

        } catch (Exception e) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            System.out.println(e);
            System.out.println(title+"---"+unit.size()+"---"+authors+"-----"+unit+"-----"+spans);
        }finally {
            if(httpResp!=null&&httpReq!=null){
                //关闭
                try {
                    httpReq.releaseConnection();
                    httpResp.close();
                } catch (IOException e) {
                    e.printStackTrace();
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
        httpReq.setHeader("Accept-Encoding", "gzip, deflate");
        httpReq.setHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        httpReq.setHeader("Connection", "keep-alive");
        httpReq.setHeader("Host", "http://c.oldg.wanfangdata.com.cn");
        httpReq.setHeader("Referer", "http://c.oldg.wanfangdata.com.cn/Periodical.aspx");
        httpReq.setHeader("Upgrade-Insecure-Requests", "1");
        httpReq.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/67.0.3396.99 Safari/537.36");
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

    public DealOnePage(String u) {
        url = u;
    }

    /*
     * 程序入口
     * */
    @Override
    public String call() {
        doGetRequest(url);
        return null;
    }

//    public static void main(String args[]){
//        String url="http://d.oldg.wanfangdata.com.cn/Periodical_zgrkx200306001.aspx";
//        doGetRequest(url);
//    }

}

