package lib

import (
	"github.com/ethereum/go-ethereum/common"
)

func SetupTest() (*Service, int, int) {
	var (
		instance       = New()
		dbHandle       = instance.OpenMemoryDB()
		_, stateHandle = instance.StateOpen(StateParams{
			DatabaseParams: DatabaseParams{DatabaseHandle: dbHandle},
			Root:           common.Hash{},
		})
	)
	return instance, dbHandle, stateHandle
}
