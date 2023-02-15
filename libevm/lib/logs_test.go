package lib

import (
	"github.com/ethereum/go-ethereum/common"
	"libevm/test"
	"math/rand"
	"reflect"
	"testing"
)

// generate the given number of random logs
func randomLogs(n int) []*Log {
	logs := make([]*Log, n)
	for j := range logs {
		log := Log{
			Address: test.RandomAddress(),
			// generate 0 to 4 random topics
			Topics: make([]common.Hash, rand.Intn(5)),
			Data:   test.RandomBytes(rand.Intn(100)),
		}
		for k := range log.Topics {
			log.Topics[k] = test.RandomHash()
		}
		logs[j] = &log
	}
	return logs
}

func TestLogs(t *testing.T) {
	instance, _, stateHandle := SetupTest()

	// use constant seed to have reproducable results
	rand.Seed(4321)

	type txLogs struct {
		txHash common.Hash
		logs   []*Log
	}

	// generate test data
	txs := make([]*txLogs, 100)
	for i := range txs {
		txs[i] = &txLogs{
			txHash: test.RandomHash(),
			logs:   randomLogs(i),
		}
	}

	for i, tx := range txs {
		_ = instance.StateSetTxContext(SetTxContextParams{
			HandleParams: HandleParams{Handle: stateHandle},
			TxHash:       tx.txHash,
			TxIndex:      i,
		})
		for _, log := range tx.logs {
			_ = instance.StateAddLog(AddLogParams{
				AccountParams: AccountParams{
					HandleParams: HandleParams{Handle: stateHandle},
					Address:      log.Address,
				},
				Topics: log.Topics,
				Data:   log.Data,
			})
		}
	}

	for _, tx := range txs {
		_, logs := instance.StateGetLogs(GetLogsParams{
			HandleParams: HandleParams{Handle: stateHandle},
			TxHash:       tx.txHash,
		})
		if !reflect.DeepEqual(tx.logs, logs) {
			t.Fatalf("unexpected logs: want %v got %v", tx.logs, logs)
		} else {
			t.Logf("validated %2v logs for tx: %v", len(tx.logs), tx.txHash.TerminalString())
		}
	}
}
