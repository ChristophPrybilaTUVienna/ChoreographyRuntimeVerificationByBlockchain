package at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.crawler;

import org.slf4j.LoggerFactory;

/**
 * Mainnet Blockchain crawler based on BlockcypherBlockChainCrawler
 */
public class BlockcypherMainnetCrawler extends BlockcypherBlockChainCrawler {

    public BlockcypherMainnetCrawler(String token) {
        super("https://api.blockcypher.com/v1/btc/main/txs/%s", "https://api.blockcypher.com/v1/btc/main/addrs/%s", token);
        logger = LoggerFactory.getLogger(BlockcypherMainnetCrawler.class);
    }

}
