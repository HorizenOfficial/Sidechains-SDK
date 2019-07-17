package com.horizen.api.http

import scorex.core.api.http.ApiRoute

trait SidechainApiRouteTrait extends ApiRoute

abstract class SidechainApiRoute
            extends SidechainApiRouteTrait with SidechainApiDirectives {

}

