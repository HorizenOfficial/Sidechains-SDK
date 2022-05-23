package evm

// #cgo CFLAGS: -g -Wall -O3 -fpic -Werror
import "C"
import (
	"errors"
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/core/rawdb"
	"github.com/ethereum/go-ethereum/core/state"
	"github.com/ethereum/go-ethereum/core/vm/runtime"
	"github.com/ethereum/go-ethereum/ethdb"
	"github.com/ethereum/go-ethereum/log"
	"libevm/overrides"
	"math/big"
)

type Instance struct {
	state     *overrides.ZenStateDB
	storage   ethdb.Database
	database  state.Database
	stateRoot common.Hash
}

func InitWithLevelDB(path string, namespace string, cache int, handles int) (*Instance, error) {
	log.Info("initializing leveldb", "path", path, "namespace", namespace, "cache", cache, "handles", handles)
	storage, err := rawdb.NewLevelDBDatabase(path, cache, handles, namespace, false)
	if err != nil {
		log.Error("failed to initialize database", "error", err)
		return nil, err
	}
	instance := &Instance{
		storage: storage,
		// TODO: enable caching later
		//database: state.NewDatabaseWithConfig(storage, &trie.Config{Cache: 16})
		database: state.NewDatabase(storage),
	}
	err = instance.SetStateRoot(common.Hash{})
	if err != nil {
		return nil, err
	}
	return instance, nil
}

func InitWithMemoryDB() (*Instance, error) {
	log.Info("initializing memorydb")
	storage := rawdb.NewMemoryDatabase()
	instance := &Instance{
		storage:  storage,
		database: state.NewDatabase(storage),
	}
	err := instance.SetStateRoot(common.Hash{})
	if err != nil {
		return nil, err
	}
	return instance, nil
}

// SetStateRoot will try to open the state database at the given root hash.
// If the root hash is zero (or the hash of zero) this will give an empty trie.
// If the hash is anything else this will result in an error if the nodes cannot be found.
func (e *Instance) SetStateRoot(stateRoot common.Hash) error {
	if e.database == nil {
		return errors.New("database not initialized")
	}
	newState, err := overrides.New(stateRoot, e.database, nil)
	if err != nil {
		log.Error("failed to open state", "rootHash", stateRoot, "error", err)
		return err
	}
	e.state = newState
	e.stateRoot = stateRoot
	return nil
}

func (e *Instance) Close() error {
	err := e.storage.Close()
	if err != nil {
		log.Error("failed to close storage", "error", err)
	}
	return err
}

func (e *Instance) configureState(cfg *runtime.Config, discardState bool) *overrides.ZenConfig {
	if discardState {
		// create a new temporary state which is used only once and should not be committed
		// TODO: we could also just force a Snapshop + Revert, is that a better option?
		tmpdb, _ := overrides.New(e.stateRoot, state.NewDatabase(e.storage), nil)
		return &overrides.ZenConfig{
			Config: cfg,
			State:  tmpdb,
		}
	} else {
		return &overrides.ZenConfig{
			Config: cfg,
			State:  e.state,
		}
	}
}

func (e *Instance) Create(input []byte, cfg *runtime.Config, discardState bool) ([]byte, common.Address, uint64, error) {
	return overrides.Create(input, e.configureState(cfg, discardState))
}

func (e *Instance) Call(address common.Address, input []byte, cfg *runtime.Config, discardState bool) ([]byte, uint64, error) {
	return overrides.Call(address, input, e.configureState(cfg, discardState))
}

// IntermediateRoot retrieves the current state root hash without actually commiting any pending changes.
func (e *Instance) IntermediateRoot() common.Hash {
	return e.state.IntermediateRoot(true)
}

// Commit any pending changes to persisted state.
func (e *Instance) Commit() (common.Hash, error) {
	stateRoot, err := e.state.Commit(true)
	if err == nil {
		err = e.state.Database().TrieDB().Commit(stateRoot, true, nil)
		if err == nil {
			// update committed state root
			e.stateRoot = stateRoot
		}
	}
	return stateRoot, err
}

func (e *Instance) ResetBalanceChanges() {
	e.state.BalanceChanges = make(overrides.BalanceLog)
}

func (e *Instance) GetBalanceChanges() overrides.BalanceLog {
	return e.state.BalanceChanges
}

func (e *Instance) SetBalance(addr common.Address, amount *big.Int) {
	e.state.StateDB.SetBalance(addr, amount)
}

func (e *Instance) AddBalance(addr common.Address, amount *big.Int) {
	// bypass our overriden functions in ZenStateDB
	e.state.StateDB.AddBalance(addr, amount)
}

func (e *Instance) SubBalance(addr common.Address, amount *big.Int) {
	// bypass our overriden functions in ZenStateDB
	e.state.StateDB.SubBalance(addr, amount)
}
