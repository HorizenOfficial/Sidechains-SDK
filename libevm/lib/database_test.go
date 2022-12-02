package lib

import (
	"crypto/ecdsa"
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/common/hexutil"
	"github.com/ethereum/go-ethereum/core/rawdb"
	"github.com/ethereum/go-ethereum/core/state"
	"github.com/ethereum/go-ethereum/crypto"
	"github.com/ethereum/go-ethereum/trie"
	"math/big"
	"testing"
)

var (
	keys  = make([]*ecdsa.PrivateKey, 1000)
	addrs = make([]common.Address, len(keys))
)

func init() {
	for i := 0; i < len(keys); i++ {
		keys[i], _ = crypto.GenerateKey()
		addrs[i] = crypto.PubkeyToAddress(keys[i].PublicKey)
	}
}

// Benchmark LevelDB and TrieDB with given cache sizes:
// This will commit N different states involving a bunch of different accounts,
// then reopen the states, read back all data and verify correctness.
func benchDatabase(b *testing.B, leveldbCache int, trieCache int) {
	// note: level db will always have at least 16MB of cache, even if giving 0 as the argument
	storage, err := rawdb.NewLevelDBDatabase(b.TempDir(), leveldbCache, 0, "zen/db/data/", false)
	db := state.NewDatabaseWithConfig(storage, &trie.Config{Cache: trieCache})

	var roots []common.Hash
	root := common.Hash{}
	// write data
	for run := 0; run < b.N; run++ {
		statedb, _ := state.New(root, db, nil)
		for i := 0; i < len(addrs); i++ {
			value := big.NewInt(int64(100000*i + run))
			addr := addrs[i]
			statedb.SetBalance(addr, value)
			statedb.SetState(addr, addr.Hash(), common.BigToHash(value))
		}
		root, err = statedb.Commit(true)
		if err != nil {
			b.Fatalf("failed to commit StateDB: %v", err)
		}
		err = statedb.Database().TrieDB().Commit(root, false, nil)
		if err != nil {
			b.Fatalf("failed to commit TrieDB: %v", err)
		}
		roots = append(roots, root)
	}
	// read data
	for run := 0; run < b.N; run++ {
		statedb, _ := state.New(roots[run], db, nil)
		for i := 0; i < len(addrs); i++ {
			want := big.NewInt(int64(100000*i + run))
			addr := addrs[i]
			if have := statedb.GetBalance(addr); have.Cmp(want) != 0 {
				b.Fatalf("incorrect balance, have %v want %v", have, want)
			}
			if have := statedb.GetState(addr, addr.Hash()).Big(); have.Cmp(want) != 0 {
				b.Fatalf("incorrect state value, have %v want %v", have, want)
			}
		}
	}
}

func BenchmarkDatabaseNoCache(b *testing.B) {
	benchDatabase(b, 0, 0)
}

func BenchmarkDatabaseLevelDbCache(b *testing.B) {
	benchDatabase(b, 64, 0)
}

func BenchmarkDatabaseTrieCache(b *testing.B) {
	benchDatabase(b, 0, 64)
}

func BenchmarkDatabaseCache(b *testing.B) {
	benchDatabase(b, 64, 64)
}

// verifies that LevelDB properly persists data to disk on close
func TestService_OpenLevelDB(t *testing.T) {
	var (
		instance = New()
		addr     = common.HexToAddress("0x0011223344556677889900112233445566778899")
		rootHash = common.Hash{}
	)
	dbPath := t.TempDir()
	for i := 1; i < 10; i++ {
		previous := big.NewInt(int64(i - 1))
		next := big.NewInt(int64(i))
		// open database
		err, dbHandle := instance.OpenLevelDB(LevelDBParams{Path: dbPath})
		if err != nil {
			t.Fatalf("failed to open database: %v", err)
		}
		err, stateHandle := instance.StateOpen(StateParams{
			DatabaseParams: DatabaseParams{DatabaseHandle: dbHandle},
			Root:           rootHash,
		})
		if err != nil {
			t.Fatalf("failed to open state: %v", err)
		}
		err, current := instance.StateGetBalance(AccountParams{
			HandleParams: HandleParams{Handle: stateHandle},
			Address:      addr,
		})
		if err != nil {
			t.Fatalf("failed to get balance: %v", err)
		}
		// verify current state
		if current.ToInt().Cmp(previous) != 0 {
			t.Fatalf("invalid state: want %v got %v", previous, current.ToInt())
		}
		// update state
		err = instance.StateSetBalance(BalanceParams{
			AccountParams: AccountParams{
				HandleParams: HandleParams{Handle: stateHandle},
				Address:      addr,
			},
			Amount: (*hexutil.Big)(next),
		})
		if err != nil {
			t.Fatalf("failed to set balance: %v", err)
		}
		err, rootHash = instance.StateCommit(HandleParams{Handle: stateHandle})
		if err != nil {
			t.Fatalf("failed to commit state: %v", err)
		}
		instance.StateClose(HandleParams{Handle: stateHandle})
		err = instance.CloseDatabase(DatabaseParams{DatabaseHandle: dbHandle})
		if err != nil {
			t.Fatalf("failed to close database: %v", err)
		}
	}
}
