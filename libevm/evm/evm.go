package evm

// #cgo CFLAGS: -g -Wall -O3 -fpic -Werror
import "C"
import (
	"errors"
	"fmt"
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/core/rawdb"
	"github.com/ethereum/go-ethereum/core/state"
	"github.com/ethereum/go-ethereum/ethdb"
	"github.com/ethereum/go-ethereum/log"
	"libevm/types"
)

type Instance struct {
	storage  ethdb.Database
	database state.Database
	statedbs map[int]*state.StateDB
	counter  int
	yolo     types.SerializableConfig
}

func New(storage ethdb.Database) *Instance {
	return &Instance{
		storage: storage,
		// TODO: enable caching
		//database: state.NewDatabaseWithConfig(storage, &trie.Config{Cache: 16})
		database: state.NewDatabase(storage),
		statedbs: make(map[int]*state.StateDB),
	}
}

func InitWithLevelDB(path string) (*Instance, error) {
	log.Info("initializing leveldb", "path", path)
	storage, err := rawdb.NewLevelDBDatabase(path, 0, 0, "zen/db/data/", false)
	if err != nil {
		log.Error("failed to initialize database", "error", err)
		return nil, err
	}
	return New(storage), nil
}

func InitWithMemoryDB() (*Instance, error) {
	log.Info("initializing memorydb")
	storage := rawdb.NewMemoryDatabase()
	return New(storage), nil
}

func (e *Instance) Close() error {
	err := e.storage.Close()
	if err != nil {
		log.Error("failed to close storage", "error", err)
	}
	return err
}

// OpenState will create a new state at the given root hash.
// If the root hash is zero (or the hash of zero) this will give an empty trie.
// If the hash is anything else this will result in an error if the nodes cannot be found.
func (e *Instance) OpenState(root common.Hash) (int, error) {
	if e.database == nil {
		return 0, errors.New("database not initialized")
	}
	// TODO: research if we want to use the snapshot feature
	statedb, err := state.New(root, e.database, nil)
	if err != nil {
		log.Error("failed to open state", "rootHash", root, "error", err)
		return 0, err
	}
	e.counter++
	e.statedbs[e.counter] = statedb
	return e.counter, nil
}

func (e *Instance) GetState(handle int) (*state.StateDB, error) {
	statedb := e.statedbs[handle]
	if statedb == nil {
		return nil, errors.New(fmt.Sprintf("invalid state handle: %d", handle))
	}
	return e.statedbs[handle], nil
}

func (e *Instance) CloseState(id int) {
	delete(e.statedbs, id)
}

//func (e *Instance) configureState(cfg *runtime.Config, discardState bool) *runtime.Config {
//	if discardState {
//		// create a new temporary state which is used only once and should not be committed
//		tmpdb, _ := state.New(e.stateRoot, state.NewDatabase(e.storage), nil)
//		cfg.State = tmpdb
//	} else {
//		cfg.State = e.state
//	}
//	return cfg
//}
//
//func (e *Instance) Create(input []byte, cfg *runtime.Config, discardState bool) ([]byte, common.Address, uint64, error) {
//	return runtime.Create(input, e.configureState(cfg, discardState))
//}
//
//func (e *Instance) Call(address common.Address, input []byte, cfg *runtime.Config, discardState bool) ([]byte, uint64, error) {
//	return runtime.Call(address, input, e.configureState(cfg, discardState))
//}
//
//// IntermediateRoot retrieves the current state root hash without actually commiting any pending changes.
//func (e *Instance) IntermediateRoot() common.Hash {
//	return e.state.IntermediateRoot(true)
//}
//
//// Commit any pending changes to persisted state.
//func (e *Instance) Commit() (common.Hash, error) {
//	stateRoot, err := e.state.Commit(true)
//	if err == nil {
//		err = e.state.Database().TrieDB().Commit(stateRoot, true, nil)
//		if err == nil {
//			// update committed state root
//			e.stateRoot = stateRoot
//		}
//	}
//	return stateRoot, err
//}
//
//func (e *Instance) SetBalance(addr common.Address, amount *big.Int) {
//	e.state.SetBalance(addr, amount)
//}
//
//func (e *Instance) AddBalance(addr common.Address, amount *big.Int) {
//	// bypass our overriden functions in ZenStateDB
//	e.state.AddBalance(addr, amount)
//}
//
//func (e *Instance) SubBalance(addr common.Address, amount *big.Int) {
//	// bypass our overriden functions in ZenStateDB
//	e.state.SubBalance(addr, amount)
//}
