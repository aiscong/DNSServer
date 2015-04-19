package edu.wisc.cs.sdn.simpledns;
import java.util.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import edu.wisc.cs.sdn.simpledns.packet.DNS;
import edu.wisc.cs.sdn.simpledns.packet.DNSQuestion;
import edu.wisc.cs.sdn.simpledns.packet.DNSResourceRecord;

public class SimpleDNS {

	private static InetAddress rootIp;
	private static int srcPort;
	private static InetAddress srcIp;
	private static String fileName;
	private static int DNSPort = 53;
	private static short queryType;
	private static boolean locked;
	private static boolean recur;
	
	private static DNS oriDNS;

	//private static String oriQuestion;
	
	private static DatagramSocket server;
	//private static Socket connection;

	private static DNS replyDNS;

	public static HashSet<Short> validType = new HashSet<Short>();
	static{
		validType.add(DNS.TYPE_A);
		validType.add(DNS.TYPE_AAAA);
		validType.add(DNS.TYPE_CNAME);
		validType.add(DNS.TYPE_NS);
	}

	public static void main(String[] args){
		System.out.println("Hello, DNS!"); 
		//EC2Entry.printBinary(EC2Entry.slashToMask(31));
		//java edu.wisc.cs.sdn.simpledns.SimpleDNS -r <root server ip> -e <ec2 csv>
		//server should listen for UDP packets on port 8053
		if(args.length != 4){
			System.out.println("Missing or too many arguments");
			System.exit(-1);
		}
		final int port = 8053;

		if(!args[0].equals("-r")) {
			System.out.println("missing root server flag");
			System.exit(-1);
		}
		if(!args[2].equals("-e")){
			System.out.println("missing ec file flag");
			System.exit(-1);
		}
		fileName = args[3];
		try {
			//System.out.println(args[1].getBytes().length);
			rootIp = InetAddress.getByName(args[1]);
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		try {
			server = new DatagramSocket(port);
			byte[] receiveData; 
			while(true){
				receiveData = new byte[1024];
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				server.receive(receivePacket); 

				DNS recDNS = DNS.deserialize(receivePacket.getData(), receivePacket.getData().length);
				//handle query
				if(recDNS.isQuery() && recDNS.getOpcode() == DNS.OPCODE_STANDARD_QUERY && !locked){
					oriDNS = recDNS;
					//locked = true;
					recur = recDNS.isRecursionDesired();
					srcIp = receivePacket.getAddress();
					srcPort = receivePacket.getPort();
					System.out.println("-----------srcPort: " + srcPort + "--------");
					handleQuery(recDNS);
					replyDNS = new DNS();
					replyDNS.setId(recDNS.getId());
					replyDNS.setQuery(false);
					replyDNS.setOpcode(DNS.OPCODE_STANDARD_QUERY);
					replyDNS.setAuthoritative(false);
					replyDNS.setTruncated(false);
					replyDNS.setRecursionDesired(false);
					replyDNS.setRecursionAvailable(false);
					replyDNS.setAuthenicated(false);

				}
				//handle reply
				else{
					//System.out.println("------------received nonquery-------------");
					if(!recDNS.getAnswers().isEmpty()){
						System.out.println("-------------has answers------------");
						for(DNSResourceRecord i: recDNS.getAnswers())
							replyDNS.addAnswer(i);
						DNSResourceRecord recRecord = recDNS.getAnswers().get(0);

						if(recRecord.getType() == queryType){
							//populate list in replyDNS
							System.out.println("-----------Receiving Final Answer: ");
							System.out.println(recDNS.getAnswers().get(0).toString());
							for(DNSResourceRecord i: recDNS.getAuthorities())
								replyDNS.addAuthority(i);
							for(DNSResourceRecord i: recDNS.getAdditional())
								replyDNS.addAdditional(i);

							byte[] replyData = new byte[1024];
							DatagramPacket replyPacket = new DatagramPacket(replyDNS.serialize(), replyDNS.serialize().length, srcIp, srcPort);

							//replyPacket.setData(replyDNS.serialize());
							//receivePacket.setAddress(srcIp);
							//receivePacket.setPort(port);
							server.send(replyPacket);
							System.out.println("srcIP: " + srcIp.toString());
							System.out.println("srcPort: " + srcPort);
							locked = false;
							System.out.println("-----------Answer Sent-----------");
						}else{
							System.out.println("------------Receiving CNAME--A Answer----------- ");
							if(!(recRecord.getType() == DNS.TYPE_CNAME)){
								System.out.println("Query for A, receiving: " + recRecord.getType() + " " + queryType);
								System.exit(-1);
							}
							
							byte[] fwdData = new byte[1024];
							DatagramPacket forwardPacket = new DatagramPacket(fwdData, fwdData.length, rootIp, DNSPort);
							/*DNS fwdQuery = new DNS();
							fwdQuery.setId((short)(recDNS.getId()+1));
							fwdQuery.setQuery(true);
							fwdQuery.setOpcode(DNS.OPCODE_STANDARD_QUERY);
							fwdQuery.setAuthoritative(false);
							fwdQuery.setTruncated(false);
							fwdQuery.setRecursionDesired(false);
							fwdQuery.setRecursionAvailable(false);
							fwdQuery.setAuthenicated(true);
							DNSQuestion fwdQuestion = new DNSQuestion(oriQuestion, queryType);
							fwdQuery.addQuestion(fwdQuestion);*/
							
							forwardPacket.setData(oriDNS.serialize());
							server.send(forwardPacket);
							
						}

					}
					else{
						System.out.println("-----------\n" + recDNS.toString() + "------------");
						if(!recur){
							for(DNSResourceRecord i: recDNS.getAuthorities())
								replyDNS.addAuthority(i);
							for(DNSResourceRecord i: recDNS.getAdditional())
								replyDNS.addAdditional(i);
							byte[] replyData = new byte[1024];
							DatagramPacket replyPacket = new DatagramPacket(replyData, replyData.length, srcIp, srcPort);

							replyPacket.setData(replyDNS.serialize());
							server.send(replyPacket);
							locked = false;
							System.out.println("---------finish sending nonrecur reply----------");
						}else{
							System.out.println("--------------continue recur-------------");
							DNSResourceRecord auth = recDNS.getAuthorities().get(0);
							DNSResourceRecord add = recDNS.getAdditional().get(0);
							if(!auth.getData().toString().equals(add.getName()))
								System.out.println("@@@@@@@@@@@@@@@@authorites and additional not matching!");
							
							InetAddress fwdAddress = InetAddress.getByName(add.getData().toString());
							
							System.out.println("-----------continue querying: " + add.getData().toString());
							
							byte[] fwdData = new byte[1024];
							DatagramPacket forwardPacket = new DatagramPacket(fwdData, fwdData.length, fwdAddress, DNSPort);
							/*DNS fwdQuery = new DNS();
							//fwdQuery.setId((short)(recDNS.getId()+1));
							fwdQuery.setQuery(true);
							fwdQuery.setOpcode(DNS.OPCODE_STANDARD_QUERY);
							fwdQuery.setAuthoritative(false);
							fwdQuery.setTruncated(false);
							fwdQuery.setRecursionDesired(false);
							fwdQuery.setRecursionAvailable(false);
							fwdQuery.setAuthenicated(false);
							DNSQuestion fwdQuestion = new DNSQuestion(oriQuestion, queryType);
							fwdQuery.addQuestion(fwdQuestion);*/
							forwardPacket.setData(oriDNS.serialize());
							server.send(forwardPacket);
							System.out.println("-----------recur------------");
						}
						
					}
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
		if(!locked){
			//oriQuestion = query.getQuestions().get(0).getName();
			queryType = query.getQuestions().get(0).getType();
		}
		System.out.println(query.toString());

		//directly forward the query packet to root server
		byte[] forwardData = new byte[1024];
		DatagramPacket forwardPacket = new DatagramPacket(forwardData, forwardData.length, rootIp, DNSPort);
		
		forwardPacket.setData(query.serialize());
		
		System.out.println(rootIp.toString());
		
		try {
			server.send(forwardPacket);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		locked = true;
		System.out.println("-----------finish forwarding pkt------------");
	}


	/**
	 * Read EC2 file
	 */
	public static List<EC2Entry> readEC2File(String fileName){
		List<EC2Entry> result = new ArrayList<EC2Entry>();
		BufferedReader in;
		try {
			in = new BufferedReader(new FileReader(fileName));
			while(in.ready()){
				String line = in.readLine();
				String ipDot = null;
				String region = null;
				int slash = -1;
				int ip = 0;
				int i;
				for(i = 0; i < line.length(); i++){
					if(line.charAt(i) == '/'){
						ipDot = line.substring(0, i);
						ip = EC2Entry.dotToInt(ipDot);
						break;
					}
				}
				i++;
				String[] splitline = line.substring(i, line.length()).split(",");
				if(splitline == null){
					System.out.println("parse error split line");
					System.exit(-1);
				}
				if(splitline.length != 2){
					System.out.println("parse error split line");
					System.exit(-1);
				}

				slash = Integer.valueOf(splitline[0]);
				if(slash != -1){
					slash = EC2Entry.slashToMask(slash);
				}
				region = splitline[2];
				result.add(new EC2Entry(slash, ip, region));
			}
			in.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(-1);
		}
		return result;
	}
}
