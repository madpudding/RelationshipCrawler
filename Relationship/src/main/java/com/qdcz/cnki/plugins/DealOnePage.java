package com.qdcz.cnki.plugins;

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
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;

/*
*
* 解析单个大学页面
* 获取论文消息
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
        cm.setMaxTotal(260);
        cm.setDefaultMaxPerRoute(60);

        proxy = new HttpHost(proxyHost, proxyPort, "http");

        credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(proxyUser, proxyPass));

        reqConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(60000)
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


    /*
    * 获取大学名称和url
    * */
    public static Map<Integer,String> doRequest(HttpRequestBase httpReq)  {
        CloseableHttpResponse httpResp = null;
        Map<Integer,String> str=new HashMap<>(); //获取大学名和论文数量
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

            String content=inputStreamToString(httpResp.getEntity().getContent());//获取内容
            byte[] data=null;
            data=content.getBytes();
            //获取大学名字论文数
            String uu = Tools.get_one_item(data, "UTF-8", "//*[@id='xw']/div[2]/dl/dd/h3//text()");
            String pageID="";//页数
            String UniverstyName="";//大学名
            int pageId=0;//页数
            int pNum=0;//页数
            if (uu == null) {
                //关闭
                httpResp.close();
                //循环直至正确获得值
                int i = 0;
                while (true) {
                    //重新创建
                    localContext = HttpClientContext.create();
                    localContext.setAuthCache(authCache);
                    httpResp = httpClient.execute(httpReq, localContext);

                    i++;
                    //创建客户端
                    byte[] dataAgin = null;
                    dataAgin = inputStreamToString(httpResp.getEntity().getContent()).getBytes();
                    uu = Tools.get_one_item(dataAgin, "UTF-8", "//*[@id='xw']/div[2]/dl/dd/h3//text()");
                    pageID = Tools.get_one_item(dataAgin, "UTF-8", "//*[@id='xw']/div[2]/dl/dd/div/ul[2]/li[2]/p[1]/span//text()");

                    if (uu != null && pageID != null) {
                        break;
                    }
                    if (i > 5) {
                        System.out.println("再次请求了" + i + "次" + "已经放弃");
                        break;
                    }
                }
            } else {
                pageID = Tools.get_one_item(data, "UTF-8", "//*[@id='xw']/div[2]/dl/dd/div/ul[2]/li[2]/p[1]/span//text()");
            }
            //名字字符串有问题故改进 去掉了（空格 985 211 教育部直属）
            UniverstyName = uu.replaceAll("\\s*", "").replaceAll("\\d+", "").replaceAll("教育部直属", "");
            //System.out.println( UniverstyName );
            String pn = pageID.replace(" 篇", "");
            //转换参数
            pageId = Integer.valueOf(pn).intValue();
            //之所以转换成double是因为整数不能多取最后一页
            double d;
            d = pageId;
            double lastId = Math.ceil(d / 20);
            pNum = Integer.parseInt(new java.text.DecimalFormat("0").format(lastId));
            //将页数和大学名返回
            str.put(pNum,UniverstyName);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                //关闭连接
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
        return str;
    }

    /**
     * 设置请求头
     *
     * @param httpReq
     */
    private static void setHeaders(HttpRequestBase httpReq) {
        httpReq.setHeader("Accept", "*/*");
        httpReq.setHeader("Accept-Encoding", "gzip, deflate");
        httpReq.setHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        httpReq.setHeader("Cache-Control","max-age=0");
        httpReq.setHeader("Connection", "keep-alive");
        httpReq.setHeader("Content-Type", "application/x-www-form-urlencoded");
        httpReq.setHeader("Host", "navi.cnki.net");
        httpReq.setHeader("Origin", "http://navi.cnki.net");
        httpReq.setHeader("Referer", "http://epub.cnki.net/KNS/brief/result.aspx?dbprefix=CDMD");
        httpReq.setHeader("Upgrade-Insecure-Requests", "1");
        httpReq.setHeader("User-Agent", "Mozilla/5.0 (iPad; CPU OS 11_0 like Mac OS X) AppleWebKit/604.1.34 (KHTML, like Gecko) Version/11.0 Mobile/15A5341f Safari/604.1");
        httpReq.setHeader("X-Requested-With", "XMLHttpRequest");
        httpReq.setHeader(switchIpHeaderKey, switchIpHeaderVal);
    }



    //第一次请求form表单参数
    public static Map<Integer,String> doPostRequest(String url) {
        Map<Integer,String> str=new HashMap<>();
        try {
            // 要访问的目标页面
            HttpPost httpPost = new HttpPost(url);
            // 设置表单参数
            List params = new ArrayList();
            params.add(new BasicNameValuePair("pcode", "CDMD"));//设置页编码
            params.add(new BasicNameValuePair("baseID", url.substring(56)));
            params.add(new BasicNameValuePair("subCode", ""));
            params.add(new BasicNameValuePair("orderBy","RT|DESC"));
            params.add(new BasicNameValuePair("scope","%u5168%u90E8"));
            params.add(new BasicNameValuePair("pidx", "0"));
            httpPost.setEntity(new UrlEncodedFormEntity(params, "utf-8"));
            str=doRequest(httpPost);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return  str;
    }

    //第二次form表单参数
    public static void doPostRequestAgin(Integer pidx,String UniverstyName,String url) {
        //转换
        int j=pidx;
        String ul="http://navi.cnki.net/knavi/PPaperDetail/GetArticleBySubjectinPage";
        try {
            for(int i=0;i<j;i++){
                // 要访问的目标页面
                HttpPost httpPost = new HttpPost(ul);
                String pid=Integer.toString(i);
                // 设置表单参数
                List params = new ArrayList();
                params.add(new BasicNameValuePair("pcode", "CDMD"));//设置页编码
                params.add(new BasicNameValuePair("baseID", url.substring(56)));
                params.add(new BasicNameValuePair("subCode", null));
                params.add(new BasicNameValuePair("orderBy","RT|DESC"));
                params.add(new BasicNameValuePair("scope","%u5168%u90E8"));
                params.add(new BasicNameValuePair("pIdx", pid));
                httpPost.setEntity(new UrlEncodedFormEntity(params, "utf-8"));
                doRequestAgin(httpPost,UniverstyName);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    //获得参数后进行第二次请求，获得具体参数
    public static void doRequestAgin(HttpRequestBase httpReq,String UniverstyName)  {
        CloseableHttpResponse httpResp = null;



        try {

            //设置存储参数的数组
            Vector<String> titles = new Vector<>();//论文名称
            String t = null;//网页首篇作者学位
            setHeaders(httpReq);

            httpReq.setConfig(reqConfig);

            CloseableHttpClient httpClient1 = HttpClients.custom()
                    .setConnectionManager(cm)
                    .setDefaultCredentialsProvider(credsProvider)
                    .build();

            AuthCache authCache = new BasicAuthCache();
            authCache.put(proxy, new BasicScheme());

            HttpClientContext localContext = HttpClientContext.create();
            localContext.setAuthCache(authCache);

            httpResp = httpClient1.execute(httpReq, localContext);


                String content=inputStreamToString(httpResp.getEntity().getContent());//获取内容
                byte[] data=null;
                data=content.getBytes();
                t = Tools.get_one_item(data, "UTF-8", "//*[@class='tableStyle']/tbody/tr[1]/td[7]//text()");
                if (t == null) {
                    httpResp.close();
                    //循环直至正确获得值
                    int j = 0;
                    while (true) {
                        j++;
                        localContext = HttpClientContext.create();
                        localContext.setAuthCache(authCache);
                        httpResp = httpClient1.execute(httpReq, localContext);
                        byte[] dataAgin = null;
                        dataAgin = inputStreamToString(httpResp.getEntity().getContent()).getBytes();
                        t = Tools.get_one_item(dataAgin, "UTF-8", "//*[@class='tableStyle']/tbody/tr[1]/td[7]//text()");
                        Tools.getMultiResultsByOneXpathPattern(data, "UTF-8", "//*[@class='tableStyle']/tbody/tr/td[2]/a/@href", titles);

                        if (t != null) {
                            break;
                        }
                        if (j > 5) {
                            System.out.println("再次请求了" + UniverstyName + "页码" +  "---" + j + "次" + "已经放弃");
                            break;
                        }
                    }
                } else {
                    //*[@class='tableStyle']/tbody/tr/td[2]/a//text()
                    Tools.getMultiResultsByOneXpathPattern(data, "UTF-8", "//*[@class='tableStyle']/tbody/tr/td[2]/a/@href", titles);

                }
                //写入文件
            FileWriter fw=null;
            File file=new File("main/resources/"+UniverstyName+".csv");

            if(!file.exists()){
                file.createNewFile();
            }
            synchronized (""){
                for (int k = 0; k < titles.size(); k++) {
                    fw=new FileWriter(file,true);
                    String str1 =  titles.get(k);
                    String str2=str1.replaceAll("\\s*","");
                    String[] str3=str2.split("&");
                    String url="http://gb.oversea.cnki.net/kcms/detail/detail.aspx?recid=&"+str3[2]+"&"+str3[3]+"&"+str3[1]+"\n";
                    System.out.println(url);
                    fw.append(url);
                    fw.flush();
                    fw.close();
                }
            }
        } catch (Exception e) {
            System.out.println(e);
        } finally {
            try {
                //关闭连接
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
        return ;
    }


    private  String url;

    public DealOnePage(String u){
        url=u;
    }



    @Override
    public List<String> call() {
        //System.out.println(url);
        //取得页数和大学名
        Map<Integer,String> str=doPostRequest(url);
        int j=0;//页数
        String UniverstyName="";//大学名
        for(Integer key : str.keySet()){
            UniverstyName = str.get(key);
            j=key;
        }
        System.out.println(UniverstyName+"========="+j+url);
        //传入页数
        doPostRequestAgin(j,UniverstyName,url);

        return null;
    }

}
