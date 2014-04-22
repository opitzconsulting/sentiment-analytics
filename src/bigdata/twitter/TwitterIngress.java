package bigdata.twitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.Test;

import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;

public class TwitterIngress {
	
	private static final int REQUIRED_ARGS = 3;
	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("EEE MMM dd HH:mm:ss ZZZ yyyy", new Locale("en"));

	public static void main(String[] args) throws Exception {
		if(args.length < REQUIRED_ARGS) 
			throw new IllegalArgumentException(String.format(
					"%s parameters required but only %s found.", REQUIRED_ARGS, args.length));
		Twitter twitter = new TwitterFactory().getInstance();
		twitter.setOAuthConsumer(
				"<<<Your OAuth Private Consumer Key>>>",
				"<<<Your OAuth Public Consumer Key>>>");
		twitter.setOAuthAccessToken(new AccessToken(
				"<<<Your OAuth Private Access Token>>>",
				"<<<Your OAuth Public Access Token>>>"));
		
		Configuration config = new Configuration();     
		FileSystem fs = FileSystem.get(config);
		long maxId = Long.MAX_VALUE;
		long sinceId = 0l;
		Path sinceFile = new Path(args[2]);
		if (fs.exists(sinceFile) && fs.isFile(sinceFile)) {
			FSDataInputStream in = fs.open(sinceFile);
			try {
				sinceId = Long.parseLong(new BufferedReader(new InputStreamReader(in)).readLine());
			} finally {
				in.close();
			}
		}
		long newSinceId = sinceId;
		FSDataOutputStream tweets_out = fs.create(new Path(args[1]));
		QueryResult results = null;
		try {
			tweets_out.writeBytes("[");
			do {
				Query query = new Query(args[0]);
				query.setCount(100);
				query.setLang("de");
				query.setResultType("recent");
				if (maxId < Long.MAX_VALUE) query.setMaxId(maxId - 1);
				if (sinceId > 0) query.setSinceId(sinceId);
				results = twitter.search(query);
				for(Status status : results.getTweets()) {
					maxId = Math.min(maxId, status.getId());
					newSinceId = Math.max(newSinceId, status.getId());
					tweets_out.writeBytes(String.format(
							"{\"text\":\"%s\",\"created_at\":\"%s\",\"user\":\"%s\"},",
							escape(status.getText()), 
							DATE_FORMAT.format(status.getCreatedAt()), 
							escape(status.getUser().getName())));
				}
			} while (results.getTweets().size() > 90);
		} finally {
			tweets_out.writeBytes("{}]");
			tweets_out.close();
		}
		FSDataOutputStream since_id_out = fs.create(new Path(args[2]), true);
		try {
			since_id_out.writeBytes(String.valueOf(newSinceId));
		} finally {
			since_id_out.close();
		}
	}
	
	private static String escape(String text) {
		return text.replaceAll("[^A-Za-z0-9äüöß ,\\.:;\\?!#@=\\/\\(\\)\\+-]", " ");
	}

}
