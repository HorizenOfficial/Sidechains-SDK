package lib

import (
	"github.com/ethereum/go-ethereum/core/state"
)

type Service struct {
	databases *Handles[*Database]
	statedbs  *Handles[*state.StateDB]
}

func New() *Service {
	return &Service{
		databases: NewHandles[*Database](),
		statedbs:  NewHandles[*state.StateDB](),
	}
}
