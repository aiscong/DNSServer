package edu.wisc.cs.sdn.simpledns;

public class EC2Entry {
	private int mask;
	private String region;
	private int ip;
	public EC2Entry(int m, int p, String r){
		mask = m;
		ip = p;
		region = r;
	}
	public int getMask(){
		return mask;
	}
	public String getRegion(){
		return region;
	}
	public int getIp(){
		return ip;
	}
	/**
	 * use xor
	 * @param slash
	 * @return
	 */
	public static int slashToMask(int slash){
		int result = -1;
		int mk = 1;
		for(int i = 0; i < (32-slash); i++){
			result ^= mk;
			mk = mk << 1;
		}
		return result;
	}

	/**
	 * dot notation to int
	 * @param n
	 */
	public static int dotToInt(String dot){
		if (dot == null)
			throw new IllegalArgumentException("Specified IPv4 address must" +
					"contain 4 sets of numerical digits separated by periods");
		String[] octets = dot.split("\\.");
		if (octets.length != 4) 
			throw new IllegalArgumentException("Specified IPv4 address must" +
					"contain 4 sets of numerical digits separated by periods");

		int result = 0;
		for (int i = 0; i < 4; ++i) {
			result |= Integer.valueOf(octets[i]) << ((3-i)*8);
		}
		return result;
	}

	public static void printBinary(int n){
		for(int i = 0; i < 32; i++){
			System.out.print(1 & (n >> (31-i)));
		}
		System.out.println();
	}
}
