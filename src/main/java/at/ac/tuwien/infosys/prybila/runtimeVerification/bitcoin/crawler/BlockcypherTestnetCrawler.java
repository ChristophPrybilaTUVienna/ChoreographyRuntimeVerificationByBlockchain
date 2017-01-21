package at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.crawler;

import org.slf4j.LoggerFactory;

/**
 * Testnet Blockchain crawler based on BlockcypherBlockChainCrawler
 */
public class BlockcypherTestnetCrawler extends BlockcypherBlockChainCrawler {

    public BlockcypherTestnetCrawler(String token) {
        super("https://api.blockcypher.com/v1/btc/test3/txs/%s", "https://api.blockcypher.com/v1/btc/test3/addrs/%s", token);
        logger = LoggerFactory.getLogger(BlockcypherTestnetCrawler.class);
    }

}
