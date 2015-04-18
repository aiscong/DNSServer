package edu.wisc.cs.sdn.simpledns;
import java.util.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;

import edu.wisc.cs.sdn.simpledns.packet.DNS;
import edu.wisc.cs.sdn.simpledns.packet.DNSQuestion;

public class SimpleDNS {
	public static HashSet<Short> validType = new HashSet<Short>();
	static{
		validType.add(DNS.TYPE_A);
		validType.add(DNS.TYPE_AAAA);
		validType.add(DNS.TYPE_CNAME);
		validType.add(DNS.TYPE_NS);
	}
	
	public static void main(String[] args){
        System.out.println("Hello, DNS!"); 
        //java edu.wisc.cs.sdn.simpledns.SimpleDNS -r <root server ip> -e <ec2 csv>
        //server should listen for UDP packets on port 8053
        if(args.length != 4){
        	System.out.println("Missing or too many arguments");
        	System.exit(-1);
        }
        int port = 8053;
        DatagramSocket server;
		Socket connection;
		if(!args[0].equals("-r")) {
			System.out.println("missing root server flag");
			System.exit(-1);
		}
		if(!args[2].equals("-e")){
			System.out.println("missing ec file flag");
			System.exit(-1);
		}
		String fileName = args[3];
		String rootip = args[1];
		try {
			
			server = new DatagramSocket(port);
			byte[] receiveData = new byte[1024]; 
			while(true){
				receiveData = new byte[1024]; 
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				server.receive(receivePacket); 
				DNS recDNS = DNS.deserialize(receivePacket.getData(), receivePacket.getData().length);
				if(recDNS.isQuery() && recDNS.getOpcode() == DNS.OPCODE_STANDARD_QUERY){
					handleQuery(recDNS);
				}
			}
		}catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * HandleQuery question
	 * 
	 */
	public static void handleQuery(DNS query){
		if(query.getQuestions().isEmpty()) return;
		System.out.println("Query is not empty");
		DNSQuestion que = query.getQuestions().get(0);
		if(!validType.contains(que.getType())) return;
		System.out.println("Query type is " + que.getType());
		
		
	}
}
