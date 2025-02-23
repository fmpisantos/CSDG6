package fct.unl.pt.csd.Repositories.Redis;
import java.util.*;
import java.util.Map.Entry;

import fct.unl.pt.csd.Entities.AuctionEntity;
import fct.unl.pt.csd.Entities.BankEntity;
import fct.unl.pt.csd.Entities.BidEntity;
import fct.unl.pt.csd.Security.SecurityConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import redis.clients.jedis.*;

import javax.persistence.Entity;

@Service
public class BankServiceReplicationJedisCluster {
	
	private Jedis jedis;
	
	private JedisCluster jedisCluster;
	
	private Set<HostAndPort> jedisClusterNodes;

	@Autowired
	public BankServiceReplicationJedisCluster() {
		
		this.jedis = new Jedis();
		this.jedisClusterNodes = new HashSet<HostAndPort>();
		
		this.jedisCluster = new JedisCluster(jedisClusterNodes);
		
		// add New Cluster Node Replicas TODO
		this.addNewClusterNodeReplica(new HostAndPort("localhost", 6378));
	}
	
	public boolean addNewClusterNodeReplica(HostAndPort hostAndPort) {
		
		String hostAndPortString = hostAndPort.getHost() + ":" + hostAndPort.getPort();
		
		
		if ( !this.jedis.sismember("replicas", hostAndPortString) ) {
			
			this.jedis.sadd( "replicas", hostAndPortString );
		
			this.jedisClusterNodes.add(hostAndPort);
			
			this.jedisCluster = new JedisCluster(this.jedisClusterNodes);
			
			this.jedis.save();
			
			return true;
			
		}
		
		return false;
	
	}
	
	
	public boolean addNewUser(String username, String password, Long amount) {
		
		Map<String, String> userEntry = this.jedis.hgetAll( "users#" + username );
		
		
		if ( ( userEntry.isEmpty() ) && ( !this.jedis.sismember("users-registered", username) ) ) {
			
			this.jedis.hset( "users#" + username, "username", username );
			
			this.jedis.hset( "users#" + username, "password", password );
			
			this.jedis.hset( "users#" + username, "amount", String.valueOf(amount) );
			
			
			this.jedis.sadd( "users-registered", username );
			
			
			this.jedis.save();
			
			
			System.out.println("REDIS Storage: New User registered!!!");
			
			
			return true;
			
		}
		else {
			
			System.err.println("REDIS Storage: User not registered!!! An User with this Username already exist!!!");
			
		}
		
		
		return false;
		
	}

	public void setAmount(String username, Long amount){
		Map<String, String> userEntry = this.jedis.hgetAll("users#" + username);


		if ( ( !userEntry.isEmpty() ) && ( this.jedis.sismember( "users-registered", username ) ) ) {
			this.jedis.hset("users#" + username, "amount", String.valueOf(amount));
			this.jedis.save();
			System.out.println("REDIS Storage: Money updated for the User!!!");
		}else
			System.err.println("REDIS Storage: Money not updated for the User!!! Don't exist any User with this Username!!!");
	}
	
	public boolean createMoney(String username, Long amount) {
		
		Map<String, String> userEntry = this.jedis.hgetAll("users#" + username);
		
		
		if ( ( !userEntry.isEmpty() ) && ( this.jedis.sismember( "users-registered", username ) ) ) {
			
			Long oldAmount = Long.parseLong(userEntry.get("amount"));
			
			Long newAmount = ( amount + oldAmount );
			
			this.jedis.hset( "users#" + username, "amount", String.valueOf(newAmount) );
			
			
			this.jedis.save();
			
			
			System.out.println("REDIS Storage: Money created for the User!!!");
			
			
			return true;
			
		}
		else {
			
			System.err.println("REDIS Storage: Money not created for the User!!! Don't exist any User with this Username!!!");
			
		}
		
		return false;
		
	}

	public long[] getTransferInfo(String from, String to, long amount){
		long[] results = new long[4];
		Map<String, String> fromEntry = this.jedis.hgetAll("users#" + from);
		Map<String, String> toEntry = this.jedis.hgetAll("users#" + to);

		if ( (( !fromEntry.isEmpty() ) && ( this.jedis.sismember( "users-registered", from ) )) && (( !toEntry.isEmpty() ) && ( this.jedis.sismember( "users-registered", to ) )) ) {
			results[0] = Long.parseLong(fromEntry.get("amount"));
			results[1] = Long.parseLong(toEntry.get("amount"));
			results[2] =(Long.parseLong(fromEntry.get("amount")))-amount;
			results[3] =Long.parseLong(toEntry.get("amount"))+amount;
			this.transferMoney(from, to, amount);
			return results;
		}
		return null;
	}
	
	
	public boolean transferMoney(String fromUsername, String toUsername, Long amount) {
		
		Map<String, String> fromUserEntry = this.jedis.hgetAll("users#" + fromUsername);
		
		Map<String, String> toUserEntry = this.jedis.hgetAll("users#" + toUsername);
		
		
		if ( ( !fromUserEntry.isEmpty() ) && ( this.jedis.sismember( "users-registered", fromUsername ) ) ) {
			
			if ( ( !toUserEntry.isEmpty() ) && ( this.jedis.sismember( "users-registered", toUsername ) ) ) {
				
				Long oldAmountFromUser = Long.parseLong( fromUserEntry.get("amount") );
				
				Long newAmountFromUser = ( oldAmountFromUser - amount );
				
				
				if ( newAmountFromUser >= 0L ) {
					
					Long oldAmountToUser = Long.parseLong( toUserEntry.get("amount") );
					
					Long newAmountToUser = ( oldAmountToUser + amount );
					
					
					this.jedis.hset( "users#" + fromUsername, "amount", String.valueOf(newAmountFromUser) );
					
					this.jedis.hset( "users#" + toUsername, "amount", String.valueOf(newAmountToUser) );
					
					
					this.jedis.save();
					
				
					System.err.println("REDIS Storage: Transfer of Money was successful!!!");
					
					
					return true;
					
				}
				else {
					
					System.err.println("REDIS Storage: Transfer of Money wasn't successful!!! The Sender User of the Transfer, don't have enough money!!!");
					
				}
			
			}
			else {
				
				System.err.println("REDIS Storage: Transfer of Money wasn't successful!!! The Receiver User of the Transfer, with the given Username doesn't exist!!!");
				
			}
				
		}
		else {
			
			System.err.println("REDIS Storage: Transfer of Money wasn't successful!!! The Sender User of the Transfer, with the given Username doesn't exist!!!");
			
		}
		
		
		return false;
		
	}
	
	
	public Long getMoney(String username) {
		
		Map<String, String> userEntry = this.jedis.hgetAll("users#" + username);
		
		
		if ( ( !userEntry.isEmpty() ) && ( this.jedis.sismember( "users-registered", username ) ) ) {
			
			Long amount = Long.parseLong(userEntry.get("amount"));
			
			return amount;
			
		}
		
		
		return null;
		
	}

	public void test(){
		//System.out.println(this.jedis.scan("").getResult());
		String[] arr = jedis.keys("users#*").toArray(new String[0]);
		for(String s : arr){
			System.out.println(s);
		}
	}

	public JSONObject getJSONArrayAndHash(){
		List<String> result = this.jedis.scan("").getResult();
		int hash = 0;
		JSONObject jsonSecure;
		Map<String, String> userEntry;
		JSONArray arr = new JSONArray();
		System.out.println(jedis.keys("users#*"));
		for(String s : jedis.keys("users#*").toArray(new String[0])){
				userEntry = this.jedis.hgetAll(s);
				jsonSecure = new JSONObject().put("username",userEntry.get("username")).put("amount",Long.parseLong(userEntry.get("amount")));
				hash = hash^jsonSecure.toString().hashCode();
				arr.put(jsonSecure);
		}
		return new JSONObject().put("arr",arr).put("hash",hash);
	}

	public void replaceUsers(JSONArray arr){
		List<String> result = this.jedis.scan("").getResult();
		int hash = 0;
		JSONObject jsonSecure;
		Map<String, String> userEntry;
		int index = 0;
		JSONObject j;
		long val;
		for(String s : result) {
			if (s.startsWith("users#") && arr.length()>index) {
				userEntry = this.jedis.hgetAll(s);
				j = arr.getJSONObject(index);
				val = j.getLong("amount");
				if(Long.parseLong(userEntry.get("amount")) != val){
					this.jedis.hset( "users#" + userEntry.get("username"), "amount", String.valueOf(val));
				}else{
					// Como nao temos delete nao existe esta possibilidade
				}
			}
			index++;
		}
		this.jedis.save();
	}

	public int getnextID(){
		return jedis.keys("auction#*").toArray(new String[0]).length;
	}
	
	public Long createAuction(String username) {
		
		Map<String, String> userEntry = this.jedis.hgetAll("users#" + username);
		AuctionEntity auctionEntity = new AuctionEntity(Long.valueOf(this.getnextID()), username);
		
		if ( ( !userEntry.isEmpty() ) && ( this.jedis.sismember( "users-registered", username ) ) ) {
			
			Long auctionEntityID = auctionEntity.getID();
			
			Map<String, String> auction = this.jedis.hgetAll("auctions#" + auctionEntityID);
			
			
			if ( ( auction.isEmpty() ) && ( !this.jedis.sismember( "auctions", String.valueOf(auctionEntityID) ) ) ) {
				
				this.jedis.hset( "auction#" + String.valueOf(auctionEntityID), "ownerUsername", username );
				
				this.jedis.hset( "auction#" + String.valueOf(auctionEntityID), "state", "opened" );
				

				this.jedis.sadd( "auctions", String.valueOf(auctionEntityID) );
				
				this.jedis.sadd( "opened-auctions", String.valueOf(auctionEntityID) );
				
				
				this.jedis.save();
				
				
				System.out.println("REDIS Storage: Auction created!!! Auction #" + auctionEntityID + " created by: " + username + "!!!");
				
				
				return auctionEntityID;
				
			}
			else {
				
				System.err.println("REDIS Storage: Auction not created!!! Already exist an Auction with the given ID!!!");
				
			}
			
		}
		else {

			System.err.println("REDIS Storage: Auction not created!!! Username doesn't exist!!!");
			
		}
		
		return Long.valueOf("-1");
		
	}

	public boolean validateByteCode(byte[] b){
		return true;
	}
	
	public boolean closeAuction(String username, Long id) {
		
		Map<String, String> userEntry = this.jedis.hgetAll("users#" + username);
		
		
		if ( ( !userEntry.isEmpty() ) && ( this.jedis.sismember( "users-registered", username ) ) ) {
			
			Long auctionEntityID = id;
			
			Map<String, String> auction = this.jedis.hgetAll("auctions#" + auctionEntityID);
			
			
			if ( ( !auction.isEmpty() ) && ( this.jedis.sismember( "auctions", String.valueOf(auctionEntityID) ) ) ) {
				
				if ( ( this.jedis.sismember( "opened-auctions", String.valueOf(auctionEntityID) ) ) && ( !this.jedis.sismember( "closed-auctions", String.valueOf(auctionEntityID) ) ) ) {
				
					String ownerUsername = this.jedis.hget("auction#" + String.valueOf(auctionEntityID), "ownerUsername");
					
					
					if (ownerUsername.equalsIgnoreCase(username)) {
						
						
						this.jedis.hset("auction#" + String.valueOf(auctionEntityID), "state", "closed");
						
						
						this.jedis.srem( "opened-auctions" , String.valueOf(auctionEntityID) );
						
						
						this.jedis.sadd( "closed-auctions" , String.valueOf(auctionEntityID) );
						
						
						this.jedis.save();
						
						
						System.out.println("REDIS Storage: The Auction was closed!!!");
						
						
						return true;
						
					}
					else {
					
						System.err.println("REDIS Storage: Auction wasn't closed!!! The User with the given Username doesn't own this Auction!!!");
						
					}
				
				}
				else {
					
					System.err.println("REDIS Storage: Auction wasn't closed!!! This Auction it's already closed!!!");
					
				}
				
			}
			else {
				
				System.err.println("REDIS Storage: Auction wasn't closed!!! Don't exist an Auction with the given ID!!!");
				
			}
			
		}
		else {

			System.err.println("REDIS Storage: Auction wasn't closed!!! Username doesn't exist!!!");
			
		}
		
		return false;
		
	}
	
	public boolean addBidToAuction(String username, AuctionEntity auctionEntity, BidEntity bidEntity) {
		Map<String, String> userEntry = this.jedis.hgetAll("users#" + username);
		if ( ( !userEntry.isEmpty() ) && ( this.jedis.sismember( "users-registered", username ) ) ) {
			Long auctionEntityID = auctionEntity.getID();
			Map<String, String> auction = this.jedis.hgetAll("auctions#" + auctionEntityID);
			if ( ( !auction.isEmpty() ) && ( this.jedis.sismember( "auctions", String.valueOf(auctionEntityID) ) ) ) {
				if ( ( this.jedis.sismember( "opened-auctions", String.valueOf(auctionEntityID) ) ) && ( !this.jedis.sismember( "closed-auctions", String.valueOf(auctionEntityID) ) ) ) {
					String ownerUsername = this.jedis.hget( "auction#" + String.valueOf(auctionEntityID), "ownerUsername" );
					if ( !ownerUsername.equalsIgnoreCase(username) ) {
						Long bidAmount = bidEntity.getAmount();
						String oldBidsFromAuction = this.jedis.hget( "auction#" + String.valueOf(auctionEntityID), "bids" );
						String[] bidsFromAuctionParts = oldBidsFromAuction.split(" \\| ");
						String lastBidString = bidsFromAuctionParts[ ( bidsFromAuctionParts.length - 1 ) ];
						BidEntity lastBidEntity = new BidEntity(lastBidString);
						if( lastBidEntity.getAmount() < bidEntity.getAmount() ) {
							if (auctionEntity.validBidAmount(bidAmount)) {
								Long bidID = bidEntity.getID();
								this.jedis.sadd( "bids" , String.valueOf(bidID) );
								if ( this.jedis.hget( "auction#" + String.valueOf(auctionEntityID), "bids" ) == null )
									this.jedis.hset( "auction#" + String.valueOf(auctionEntityID), "bids", bidEntity.toString() );
								else {
									String updatedBidsFromAuction = oldBidsFromAuction + " | " + bidEntity.toString();
									this.jedis.hset( "auction#" + String.valueOf(auctionEntityID), "bids", updatedBidsFromAuction );
								}
								if ( this.jedis.hget( "users#" + username, "bids" ) == null )
									this.jedis.hset( "users#" + username, "bids", bidEntity.toString() );
								else {
									String oldBidsFromUser = this.jedis.hget( "users#" + username, "bids" );
									String updatedBidsFromUser = oldBidsFromUser + " | " + bidEntity.toString();
									this.jedis.hset( "users#" + username, "bids", updatedBidsFromUser );
								}
								this.jedis.save();
								System.out.println("REDIS Storage: Bid was added to the Auction!!! "
										         + "Bid #" + bidEntity.getID() + " added to the Auction #" + auctionEntityID + "!!!");
								return true;
							}
				        	else
				        		System.err.println("REDIS Storage: Bid wasn't added to the Auction!!! "
									   	         + "The Bid's Amount for this Auction needs to be higher than the current highest one!!!");
						}
						else
							System.err.println("REDIS Storage: Bid wasn't added to the Auction!!! "
								   	         + "The Bid's Amount for this Auction needs to be higher than the current highest one!!!");
					}
					else
						System.err.println("REDIS Storage: Bid wasn't added to the Auction!!! The User with the given Username doesn't own this Auction!!!");
				}
				else
					System.err.println("REDIS Storage: Bid wasn't added to the Auction!!! It's only possible to add Bids to Opened Auctions!!!");
			}
			else
				System.err.println("REDIS Storage: Bid wasn't added to the Auction!!! Don't exist an Auction with the given ID!!!");
		}
		else
			System.err.println("REDIS Storage: Bid wasn't added to the Auction!!! Username doesn't exist!!!");
		return false;
	}
	
	public void getCurrentOpenedAuctions() {
		
		Set<String> openedAuctions = this.jedis.smembers( "opened-auctions" );
		
		
		if ( openedAuctions.isEmpty() ) {
			
			System.out.println("There's no Opened Auctions yet!!!");
			
		}
		else {
			
			for ( String openedAuctionID : openedAuctions ) {
				
				System.out.println("INFO for Auction #" + openedAuctionID);
				
				
				Map<String, String> openedAuctionFields = this.jedis.hgetAll( "auction#" + openedAuctionID );
				
				
				for ( Entry<String, String> openedAuctionFieldsEntry : openedAuctionFields.entrySet() ) {
					
					System.out.println();
					
					System.out.println(" - " + openedAuctionFieldsEntry.getKey() + ":");
					
					System.out.println("    -> " + openedAuctionFieldsEntry.getValue() + ";");
					
				}
				
			}
		
		}
		
	}
	
	public void getCurrentClosedAuctions() {
		
		Set<String> closedAuctions = this.jedis.smembers( "closed-auctions" );
		
		
		if ( closedAuctions.isEmpty() ) {
			
			System.out.println("REDIS STORAGE: There's no Closed Auctions yet!!!");
			
		}
		else {
			
			for ( String closedAuctionID : closedAuctions ) {
				
				System.out.println("INFO for Auction #" + closedAuctionID);
				
				
				Map<String, String> closedAuctionFields = this.jedis.hgetAll( "auction#" + closedAuctionID );
				
				
				for ( Entry<String, String> closedAuctionFieldsEntry : closedAuctionFields.entrySet() ) {
					
					System.out.println();
					
					System.out.println(" - " + closedAuctionFieldsEntry.getKey() + ":");
					
					System.out.println("    -> " + closedAuctionFieldsEntry.getValue() + ";");
					
				}
				
			}
		
		}
		
	}
	
	public void getCurrentBidsFromAuction(Long auctionID) {
		
		Set<String> auctions = this.jedis.smembers( "auctions" );
		
		
		if ( auctions.isEmpty() ) {
			
			System.out.println("REDIS STORAGE: There's no Auctions yet!!!");
			
		}
		else {
			
			if ( this.jedis.sismember( "auction", String.valueOf(auctionID) ) ) {
				
				System.out.println("INFO for Auction #" + auctionID);
				
				
				String auctionBids = this.jedis.hget( "auction#" + auctionID , "bids" );
				
				
				System.out.println(auctionBids);
			
			}
			else {
	
				System.err.println("REDIS Storage: There's no Auction with th given ID!!!");
									
			}
			
		}
		
	}
	
	
	public void getBidsFroUser(String username) {
		
		Map<String, String> userEntry = this.jedis.hgetAll("users#" + username);
		
		
		if ( ( !userEntry.isEmpty() ) && ( this.jedis.sismember( "users-registered", username ) ) ) {
		
			if ( this.jedis.hget( "users#" + username, "bids" ) != null ) {
				
				System.out.println("Bids made by the User: " + username);
				
				System.out.println( this.jedis.hget( "users#" + username, "bids" ) );
				
			}
			else {
				
				System.err.println("REDIS Storage: There's no Bids made by this User!!!");
				
			}
			
		}
		else {
		
			System.err.println("REDIS Storage: Don't exist any User with this Username!!!");	
			
		}
		
	}
	
	
	public BidEntity checkWinnerBidFromClosedAuction(AuctionEntity auctionEntity) {
		
		Set<String> closedAuctions = this.jedis.smembers( "closed-auctions" );
		
		Long auctionID = auctionEntity.getID();
		
		
		if ( this.jedis.sismember( "auctions", String.valueOf( String.valueOf(auctionID) ) ) ) {
			
			if ( ( closedAuctions.contains( String.valueOf(auctionID) ) ) 
				       && ( auctionEntity.getState().equalsIgnoreCase("closed") ) 
				       && ( this.jedis.hget( "auctions#" + String.valueOf(auctionID), "state" ).equalsIgnoreCase("closed") ) ) {
					
				
				if ( this.jedis.hget( "auctions#" + String.valueOf(auctionID), "bids" ) != null ) {
				
					String bidsFromAuction = this.jedis.hget( "auctions#" + String.valueOf(auctionID), "bids" );
					
					String[] bidsFromAuctionParts = bidsFromAuction.split(" \\| ");
					
			        String lastBidString = bidsFromAuctionParts[ ( bidsFromAuctionParts.length - 1 ) ];
					
			        
			        BidEntity bidEntity = new BidEntity(lastBidString);
			        
			        
			        System.out.println("The Winner Bid for this Closed Auction is: " + bidEntity.toString());
			        
			        
			        return bidEntity;
			        
				}
				else {
					
					System.err.println("REDIS STORAGE: It wasn't possible to check the Winner Bid from the Closed Auction!!! The Closed Auction with the given ID doesn't have any Bid made!!!");
					
				}
				
			}
			else {
				
				System.err.println("REDIS STORAGE: It wasn't possible to check the Winner Bid from the Closed Auction!!! The Auction with the given ID isn't closed yet!!!");
			
			}
			
		}
		else {
			
			System.err.println("REDIS STORAGE: It wasn't possible to check the Winner Bid from the Closed Auction!!! The Auction with the given ID don't exist!!!");			
			
		}
				
		return null;
		
	}

	public String getSubject(String token) {
		byte[] signingKey = SecurityConstants.JWT_SECRET.getBytes();
		Jws parsedToken = Jwts.parser()
				.setSigningKey(signingKey)
				.parseClaimsJws(token.replace("Bearer ", ""));
		Claims claims = (Claims) parsedToken.getBody();
		System.out.println(claims.getSubject());
		return claims
				.getSubject();
	}
}
