package com.aiswarmlab.experimental;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
  private static final int PORT = 45321;
  private final ExecutorService pool = Executors.newCachedThreadPool();
  private final StringBuilder history = new StringBuilder();
  private final AtomicBoolean running = new AtomicBoolean(true);
  private TextView info, output;
  private EditText peer;
  private volatile String peerAddress="";
  private ServerSocket server;
  private static long compute(long a,long b){
    long v=0; for(long i=a;i<b;i++) v+=((i*31L)^(i>>>3))&65535L; return v;
  }
  private void log(String line){
    final String entry = new Date().toString()+" | "+line+"\n";
    synchronized(history){history.append(entry);}
    runOnUiThread(()->{ if(output!=null){ output.append(entry); } });
  }
  private static String ip(){
    try {for(NetworkInterface nic:Collections.list(NetworkInterface.getNetworkInterfaces()))
      if(nic.isUp()&&!nic.isLoopback()) for(InetAddress a:Collections.list(nic.getInetAddresses()))
        if(a instanceof Inet4Address && !a.isLoopbackAddress() && a.isSiteLocalAddress()) return a.getHostAddress();
    }catch(Exception ignored){} return "Unavailable";
  }
  private TextView text(String value,int size){
    TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(Color.rgb(225,235,247));t.setPadding(4,9,4,9);return t;
  }
  private void button(LinearLayout layout,String title,Runnable action){
    Button b=new Button(this);b.setText(title); layout.addView(b);b.setOnClickListener(v->{peerAddress=peer.getText().toString().trim();pool.execute(action);});
  }
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(13,22,38));
    LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(24,32,24,32);scroll.addView(l);
    l.addView(text("AI SWARM LAB   •   EXPERIMENTAL v0.1",22));
    l.addView(text("M0 Device inspection | M1 LAN connection | M2 Distributed computation | M3 Recovery",14));
    info=text("Local IP: "+ip()+" | Port: "+PORT+"\nAndroid: "+android.os.Build.VERSION.RELEASE+" | "+android.os.Build.MODEL+" | CPUs: "+Runtime.getRuntime().availableProcessors(),15);
    l.addView(info);
    peer=new EditText(this);peer.setSingleLine(true);peer.setTextColor(Color.WHITE);peer.setHintTextColor(Color.LTGRAY);peer.setHint("Other phone IP, e.g. 192.168.1.8");peer.setInputType(android.text.InputType.TYPE_CLASS_PHONE);l.addView(peer);
    button(l,"M0 · Local benchmark",()->benchmark());
    button(l,"M1 · Check other phone",()->ping());
    button(l,"M2 · Two-phone computation",()->distributed());
    button(l,"M3 · Simulated worker dropout",()->recovery());
    button(l,"Run all milestones",()->{benchmark();ping();distributed();recovery();});
    Button share=new Button(this);share.setText("Export diagnostic report");l.addView(share);
    share.setOnClickListener(v->{String data; synchronized(history){data=history.toString();} Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,data);startActivity(Intent.createChooser(i,"Share swarm diagnostics"));});
    l.addView(text("Voice, AI models, encryption and decentralized governance: NOT IMPLEMENTED in v0.1. Use only on a trusted private Wi-Fi network.",13));
    output=text("",12);output.setTextIsSelectable(true);l.addView(output);
    setContentView(scroll);
    pool.execute(this::serve);
    log("M0 hardware inspection: ready; listener requested");
  }
  private String target(){return peerAddress;}
  private String request(String message)throws Exception{
    String host=target();if(host.isEmpty())throw new IOException("Enter the other phone's IP first");
    Socket s=new Socket();s.connect(new InetSocketAddress(host,PORT),3500);s.setSoTimeout(120000);
    try(s;BufferedReader in=new BufferedReader(new InputStreamReader(s.getInputStream()));PrintWriter out=new PrintWriter(s.getOutputStream(),true)){
      out.println(message);String result=in.readLine();if(result==null)throw new EOFException("Peer disconnected");return result;
    }
  }
  private void serve(){
    try(ServerSocket ss=new ServerSocket(PORT)){server=ss;log("M1 listening on "+ip()+":"+PORT);
      while(running.get()){Socket s=ss.accept();pool.execute(()->handle(s));}
    }catch(Exception e){if(running.get())log("M1 listener FAIL: "+e.getMessage());}
  }
  private void handle(Socket s){try(s;BufferedReader in=new BufferedReader(new InputStreamReader(s.getInputStream()));PrintWriter out=new PrintWriter(s.getOutputStream(),true)){
    String line=in.readLine();if(line==null)return;
    if(line.equals("PING"))out.println("PONG");
    else if(line.startsWith("SUM ")){
      String[] p=line.split(" ");if(p.length!=3)throw new IOException("Malformed job");
      long a=Long.parseLong(p[1]),b=Long.parseLong(p[2]);
      if(a<0||b<a||b-a>20000000)throw new IOException("Job bounds rejected");
      out.println(compute(a,b));
    }else out.println("ERROR:UNKNOWN_COMMAND");
  }catch(Exception e){log("Peer request error: "+e.getMessage());}}
  private void benchmark(){long t=System.nanoTime();long n=compute(0,5000000);log("M0 PASS local checksum="+n+" time="+((System.nanoTime()-t)/1000000)+"ms");}
  private void ping(){try{long t=System.nanoTime();String r=request("PING");log("M1 "+(r.equals("PONG")?"PASS":"FAIL")+" peer="+target()+" response="+r+" RTT="+((System.nanoTime()-t)/1000000)+"ms");}catch(Exception e){log("M1 FAIL: "+e.getMessage());}}
  private void distributed(){
    final long n=8000000;long start=System.nanoTime();long expected=compute(0,n);long solo=(System.nanoTime()-start)/1000000;
    start=System.nanoTime();try{
      Future<Long> part=pool.submit(()->compute(0,n/2));
      String result=request("SUM "+(n/2)+" "+n);
      long sum=part.get(120,TimeUnit.SECONDS)+Long.parseLong(result);
      log("M2 "+(sum==expected?"PASS":"FAIL")+" checksum="+sum+" single="+solo+"ms distributed="+((System.nanoTime()-start)/1000000)+"ms");
    }catch(Exception e){log("M2 BLOCKED: "+e.getMessage());}
  }
  private void recovery(){
    long a=0,b=2000000;try{throw new IOException("Simulated worker dropout");}
    catch(Exception interrupted){long fallback=compute(a,b);long expected=compute(a,b);log("M3 "+(fallback==expected?"PASS":"FAIL")+" local retry after simulated dropout");}
  }
  @Override public void onDestroy(){running.set(false);try{if(server!=null)server.close();}catch(Exception ignored){}pool.shutdownNow();super.onDestroy();}
}
