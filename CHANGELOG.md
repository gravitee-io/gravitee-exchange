## [1.9.1](https://github.com/gravitee-io/gravitee-exchange/compare/1.9.0...1.9.1) (2025-10-21)


### Bug Fixes

* **controller:** handle WebSocket authentication asynchronously ([6a316f8](https://github.com/gravitee-io/gravitee-exchange/commit/6a316f8f02c042be4d2f4c8c912f30d820ded81e))
* **controller:** improve logging by prefixing with the identifyConfig id ([244a1fd](https://github.com/gravitee-io/gravitee-exchange/commit/244a1fd68289e671376c55ee340508c6f82c5bce))

# [1.9.0](https://github.com/gravitee-io/gravitee-exchange/compare/1.8.6...1.9.0) (2025-08-28)


### Features

* websocket client proxy configuration ([cb762eb](https://github.com/gravitee-io/gravitee-exchange/commit/cb762ebb55bed8898fd2cd378f8220662d4a42d6))

## [1.8.6](https://github.com/gravitee-io/gravitee-exchange/compare/1.8.5...1.8.6) (2025-08-06)


### Bug Fixes

* set tcpKeepAlive for httpClient used by Connector ([d74cced](https://github.com/gravitee-io/gravitee-exchange/commit/d74cced357c9041700ef2c279159fbb634deccab))
* start pingTask on Connector side once HelloReply received ([d7618f6](https://github.com/gravitee-io/gravitee-exchange/commit/d7618f6b2c36abf4b7156eea1a3e14a929ff2856))

## [1.8.5](https://github.com/gravitee-io/gravitee-exchange/compare/1.8.4...1.8.5) (2025-07-25)


### Bug Fixes

* add targetId in log messages ([f962515](https://github.com/gravitee-io/gravitee-exchange/commit/f9625158b861cd28383068a313276c0408484c0f))
* log an error message when healthcheck can't be send ([b0c4649](https://github.com/gravitee-io/gravitee-exchange/commit/b0c4649a75d2d84493b4531a3fa3490ec2f69e76))
* unregister channel when healthcheck reply is unhealthy ([a80d01e](https://github.com/gravitee-io/gravitee-exchange/commit/a80d01ee9d7d9be82faa1c3f69ffac7da13fd8d2))

## [1.8.4](https://github.com/gravitee-io/gravitee-exchange/compare/1.8.3...1.8.4) (2025-07-22)


### Bug Fixes

* add auto-reconnection capability to ExchangeConnector ([fc3d148](https://github.com/gravitee-io/gravitee-exchange/commit/fc3d148ad3c9591426c552f9e89de4e7926bfb67))

## [1.8.3](https://github.com/gravitee-io/gravitee-exchange/compare/1.8.2...1.8.3) (2025-02-10)


### Bug Fixes

* add trace logging the payload sent through the socket ([bd9743a](https://github.com/gravitee-io/gravitee-exchange/commit/bd9743a4eb046896ae0c4f61ef99eed16d2bc275))

## [1.8.2](https://github.com/gravitee-io/gravitee-exchange/compare/1.8.1...1.8.2) (2024-07-26)


### Bug Fixes

* properly handle blocking accesses on cache and queue ([53e6f11](https://github.com/gravitee-io/gravitee-exchange/commit/53e6f11c2d9b4dc5cb81ce5699a88e44a98b8a8d))
* put some logs on trace level ([ca81809](https://github.com/gravitee-io/gravitee-exchange/commit/ca818097de949a3b50199170b52e1d9adee5a63c))

## [1.8.1](https://github.com/gravitee-io/gravitee-exchange/compare/1.8.0...1.8.1) (2024-07-26)


### Bug Fixes

* bump node version to 6.0.3 ([3aa43e6](https://github.com/gravitee-io/gravitee-exchange/commit/3aa43e6d08fbff6acfd93f53c40fd34581b89865))

# [1.8.0](https://github.com/gravitee-io/gravitee-exchange/compare/1.7.5...1.8.0) (2024-07-25)


### Bug Fixes

* bump gravitee node version ([586558f](https://github.com/gravitee-io/gravitee-exchange/commit/586558f434d10c95e24281d9975f263918a0ad8e))
* do some code cleaning ([cc61181](https://github.com/gravitee-io/gravitee-exchange/commit/cc611813f4543265482be3c13bf269d8bb13dc8b))
* reject new incoming websocket connection when controller is shutting down ([f982401](https://github.com/gravitee-io/gravitee-exchange/commit/f9824014ad93bafbc7d75c3d47aebba8d09fee9a))
* rework stop method to make them block ([a1255cf](https://github.com/gravitee-io/gravitee-exchange/commit/a1255cf0a24b2dfb3b9a68844c125fb50aa26cd6))
* use a queue instead of relying on primary member condition ([9f1d314](https://github.com/gravitee-io/gravitee-exchange/commit/9f1d314f317366631b2f623a962a35f893cf663f))


### Features

* implement a healthcheck mechanism on candidate channel ([7aa9f8a](https://github.com/gravitee-io/gravitee-exchange/commit/7aa9f8a9ef01da4095084c4bc3308c31e7efbf98))

## [1.7.5](https://github.com/gravitee-io/gravitee-exchange/compare/1.7.4...1.7.5) (2024-07-23)


### Bug Fixes

* targets endpoint was returning empty array when target doesn't have batchs ([5aeb3bb](https://github.com/gravitee-io/gravitee-exchange/commit/5aeb3bbe8765050cdff3415c646cb3d2a559bedc))

## [1.7.4](https://github.com/gravitee-io/gravitee-exchange/compare/1.7.3...1.7.4) (2024-07-23)


### Bug Fixes

* properly update primary status when updating metrics ([0000188](https://github.com/gravitee-io/gravitee-exchange/commit/0000188f7414d01341c7644e561a8707f0e139ae))

## [1.7.3](https://github.com/gravitee-io/gravitee-exchange/compare/1.7.2...1.7.3) (2024-07-23)


### Bug Fixes

* improve primary command handling ([e13f189](https://github.com/gravitee-io/gravitee-exchange/commit/e13f189ffc3709ca45b71e2f6c9ea67788e6f48d))
* improve some logs ([a0e811c](https://github.com/gravitee-io/gravitee-exchange/commit/a0e811c00ded1daeca69d3f1c7a441740ae4b4fb))

## [1.7.2](https://github.com/gravitee-io/gravitee-exchange/compare/1.7.1...1.7.2) (2024-07-22)


### Bug Fixes

* add counter on inflight command on websocket channel ([45aa323](https://github.com/gravitee-io/gravitee-exchange/commit/45aa323cd985436056889040b12a9d5e0edcbd0d))
* correct a typo on tests ([11b2f62](https://github.com/gravitee-io/gravitee-exchange/commit/11b2f62e0cf4b0031feb16a4e3c12a361890ecde))
* rework re-balancing to cover missing case ([816ff4c](https://github.com/gravitee-io/gravitee-exchange/commit/816ff4c65aca5e8b7f98133aec975a978bb5de7d))

## [1.7.1](https://github.com/gravitee-io/gravitee-exchange/compare/1.7.0...1.7.1) (2024-07-22)


### Bug Fixes

* fix an issue with active filter ([44b777b](https://github.com/gravitee-io/gravitee-exchange/commit/44b777bcab07a37857c57893eff37766871e7121))

# [1.7.0](https://github.com/gravitee-io/gravitee-exchange/compare/1.6.1...1.7.0) (2024-07-19)


### Features

* add management endpoint on core api ([d9609f6](https://github.com/gravitee-io/gravitee-exchange/commit/d9609f605d7b32ec366706ec08b5cb3f80adde13))

## [1.6.1](https://github.com/gravitee-io/gravitee-exchange/compare/1.6.0...1.6.1) (2024-06-27)


### Bug Fixes

* log the identified key instead of the key when using deprecated configuration ([9fd77fb](https://github.com/gravitee-io/gravitee-exchange/commit/9fd77fb2d1b74a1fc7190395abb179d0330ba8d6))

# [1.6.0](https://github.com/gravitee-io/gravitee-exchange/compare/1.5.2...1.6.0) (2024-06-18)


### Features

* add new metrics information ([3df8351](https://github.com/gravitee-io/gravitee-exchange/commit/3df83517b6ff26831ce805a6333594a46f220c5c))
* implement rebalance mechanism ([834bfed](https://github.com/gravitee-io/gravitee-exchange/commit/834bfede455a3689c3c90d8aea0b5e8324fe44df))

## [1.5.2](https://github.com/gravitee-io/gravitee-exchange/compare/1.5.1...1.5.2) (2024-05-30)


### Bug Fixes

* set default enum value for CommandStatus ([a9bf170](https://github.com/gravitee-io/gravitee-exchange/commit/a9bf170f70fe934a3835ac3f6fcd28dfd38027e5))

## [1.5.1](https://github.com/gravitee-io/gravitee-exchange/compare/1.5.0...1.5.1) (2024-05-28)


### Bug Fixes

* avoid warn log when the timeout is inevitable ([c533e3b](https://github.com/gravitee-io/gravitee-exchange/commit/c533e3b69c19709cacf73515090b57ed0f4e8767))

# [1.5.0](https://github.com/gravitee-io/gravitee-exchange/compare/1.4.2...1.5.0) (2024-04-29)


### Bug Fixes

* update batch error details when batch command fails ([44301c7](https://github.com/gravitee-io/gravitee-exchange/commit/44301c76447083e803119c95ce4ad806b6ae5720))


### Features

* improve batch retry scheduler ([34584aa](https://github.com/gravitee-io/gravitee-exchange/commit/34584aa5090626915408691c8559cab866f70dbf))

## [1.4.2](https://github.com/gravitee-io/gravitee-exchange/compare/1.4.1...1.4.2) (2024-04-15)


### Bug Fixes

* add throwable to internal error warn log ([1ef23d7](https://github.com/gravitee-io/gravitee-exchange/commit/1ef23d70c196a009d3fb1fd13da61fee55228def))

## [1.4.1](https://github.com/gravitee-io/gravitee-exchange/compare/1.4.0...1.4.1) (2024-04-12)


### Bug Fixes

* handle internal error when handling command ([2198d43](https://github.com/gravitee-io/gravitee-exchange/commit/2198d43cac3aa86324f2fe23ee5de1901cb8a099))

# [1.4.0](https://github.com/gravitee-io/gravitee-exchange/compare/1.3.0...1.4.0) (2024-04-11)


### Features

* **controller:** register listener to listen primary channel eviction ([790c4ad](https://github.com/gravitee-io/gravitee-exchange/commit/790c4ad5827c7a3f4628d4a9779e88818141cf49))

# [1.3.0](https://github.com/gravitee-io/gravitee-exchange/compare/1.2.5...1.3.0) (2024-04-11)


### Features

* **connector:** handle URL containing a path ([6094748](https://github.com/gravitee-io/gravitee-exchange/commit/6094748ee00ef53b30c0e9cc58791c598895b3e0))

## [1.2.5](https://github.com/gravitee-io/gravitee-exchange/compare/1.2.4...1.2.5) (2024-04-10)


### Bug Fixes

* handle endpoint url containing underscore ([df05b82](https://github.com/gravitee-io/gravitee-exchange/commit/df05b820f8948693a0b8d40ff39bc821a4ea38a4))

## [1.2.4](https://github.com/gravitee-io/gravitee-exchange/compare/1.2.3...1.2.4) (2024-04-09)


### Bug Fixes

* **deps:** update dependency jakarta.annotation:jakarta.annotation-api to v3 ([64c80b2](https://github.com/gravitee-io/gravitee-exchange/commit/64c80b2c8636cfe5f724398b88467ac7840dcf7b))

## [1.2.3](https://github.com/gravitee-io/gravitee-exchange/compare/1.2.2...1.2.3) (2024-04-05)


### Bug Fixes

* add target id on adapter interface ([5cb7ec9](https://github.com/gravitee-io/gravitee-exchange/commit/5cb7ec9e49ae7664d5cb3cc9073030aed31f579f))
* ignore new channel without target ([94e1c8b](https://github.com/gravitee-io/gravitee-exchange/commit/94e1c8bc3ed642d186cbe09e142afd11683c4406))

## [1.2.2](https://github.com/gravitee-io/gravitee-exchange/compare/1.2.1...1.2.2) (2024-04-04)


### Bug Fixes

* increase connector retry factor ([25fd826](https://github.com/gravitee-io/gravitee-exchange/commit/25fd826326091bd96c277abae4b296a3138f4a62))

## [1.2.1](https://github.com/gravitee-io/gravitee-exchange/compare/1.2.0...1.2.1) (2024-03-29)


### Bug Fixes

* rework primary channel election ([c3c0a27](https://github.com/gravitee-io/gravitee-exchange/commit/c3c0a270dab774b6b744105c450024c8f552a56c))

# [1.2.0](https://github.com/gravitee-io/gravitee-exchange/compare/1.1.0...1.2.0) (2024-03-27)


### Features

* use exponential backoff strategy for reconnection ([acfa74f](https://github.com/gravitee-io/gravitee-exchange/commit/acfa74f8288a47da97f65bdd6e16b5c4db825de5))

# [1.1.0](https://github.com/gravitee-io/gravitee-exchange/compare/1.0.2...1.1.0) (2024-03-26)


### Bug Fixes

* check targetId before unregistering the connector ([86d2829](https://github.com/gravitee-io/gravitee-exchange/commit/86d28299c5c9ab14759f4924c30aa5cef43c5141))
* do not log the error in the log informing retries have stop ([7c8a146](https://github.com/gravitee-io/gravitee-exchange/commit/7c8a14654d55b6b33f6ab284a8eb899f35820ccb))
* use default implementation for Command/Reply adapters declaration ([6b56b3a](https://github.com/gravitee-io/gravitee-exchange/commit/6b56b3a790e972d30577cbb441ebb9ffc5a13e80))


### Features

* improve handshake error handling ([90ecdf2](https://github.com/gravitee-io/gravitee-exchange/commit/90ecdf25bc2d73de8a19928cde8b662213104870))

## [1.0.2](https://github.com/gravitee-io/gravitee-exchange/compare/1.0.1...1.0.2) (2024-03-22)


### Bug Fixes

* adapt GoodByeCommand for legacy protocol ([46a66c2](https://github.com/gravitee-io/gravitee-exchange/commit/46a66c286c1892a6501c0ab1b800a3a1f3867b84))

## [1.0.1](https://github.com/gravitee-io/gravitee-exchange/compare/1.0.0...1.0.1) (2024-03-22)


### Bug Fixes

* add message to the cluster exception ([8193984](https://github.com/gravitee-io/gravitee-exchange/commit/81939842abf46e86c9d9168b8b28b5eeb16842bb))
* verify any existing channel to avoid waiting cluster timeout ([486d562](https://github.com/gravitee-io/gravitee-exchange/commit/486d562cb476e611c32ce4a6023e403a0bc5b150))

## [1.0.1](https://github.com/gravitee-io/gravitee-exchange/compare/1.0.0...1.0.1) (2024-03-20)


### Bug Fixes

* add message to the cluster exception ([8193984](https://github.com/gravitee-io/gravitee-exchange/commit/81939842abf46e86c9d9168b8b28b5eeb16842bb))
* verify any existing channel to avoid waiting cluster timeout ([486d562](https://github.com/gravitee-io/gravitee-exchange/commit/486d562cb476e611c32ce4a6023e403a0bc5b150))

# 1.0.0 (2024-03-19)


### Features

* first version of framework ([189937c](https://github.com/gravitee-io/gravitee-exchange/commit/189937c4e9fa71c35a44af21e429a84d1d3d4ec3))

# [1.0.0-alpha.7](https://github.com/gravitee-io/gravitee-exchange/compare/1.0.0-alpha.6...1.0.0-alpha.7) (2024-03-15)


### Bug Fixes

* add missing provided scope ([832a90c](https://github.com/gravitee-io/gravitee-exchange/commit/832a90ca17a7233030b9e62047a80e26b5081ea6))
* fix some edge case issue on unit test ([f21cd3c](https://github.com/gravitee-io/gravitee-exchange/commit/f21cd3cca77ff612846a2090e2941c4bc302ba41))

# [1.0.0-alpha.6](https://github.com/gravitee-io/gravitee-exchange/compare/1.0.0-alpha.5...1.0.0-alpha.6) (2024-03-14)


### Features

* allow defining fallback configuration key ([4bed7a3](https://github.com/gravitee-io/gravitee-exchange/commit/4bed7a38d9c15a8191841a00b7d6aaaffe9cb7d3))

# [1.0.0-alpha.5](https://github.com/gravitee-io/gravitee-exchange/compare/1.0.0-alpha.4...1.0.0-alpha.5) (2024-03-05)


### Bug Fixes

* adapte connector log during connection ([59f4d15](https://github.com/gravitee-io/gravitee-exchange/commit/59f4d153818508b84c08d9b269066c5e8784bdf8))
* add command id on clusteredReply to manage no reply ([1bd10ab](https://github.com/gravitee-io/gravitee-exchange/commit/1bd10abb9cd0e7f8cbb64db2132658551cae1b89))
* add legacy controller path ([2016430](https://github.com/gravitee-io/gravitee-exchange/commit/2016430d832e8c3b7e5f13804361403792c6aa53))
* add missing client auth default value ([404bdde](https://github.com/gravitee-io/gravitee-exchange/commit/404bdde9398b0b92cf4bcb333bb069307f73ec71))
* add missing space on legacy prefixes ([dcd8ecf](https://github.com/gravitee-io/gravitee-exchange/commit/dcd8ecf0366de4d481cab262f7c6fab523f364cd))
* clean default GoodByeCommandHandler from reconnection mechanism ([7e84b4f](https://github.com/gravitee-io/gravitee-exchange/commit/7e84b4f773a7a2329fcbb7b4f172169a5c62a4d6))
* correct an issue with reply adapter wrongly used ([94684f3](https://github.com/gravitee-io/gravitee-exchange/commit/94684f323c99d0ad4f1f421e32cbe5bf25ac4c56))
* do not failed on no reply exception ([4703c83](https://github.com/gravitee-io/gravitee-exchange/commit/4703c834783fe68a9d8b5122a3ebcfedd7c5e910))
* dont use cause to log error ([c049c8f](https://github.com/gravitee-io/gravitee-exchange/commit/c049c8f311c2a9d9f55d1231e05cfbf9366f0a21))
* filter null channel on stop to avoid NPE ([3ba8c76](https://github.com/gravitee-io/gravitee-exchange/commit/3ba8c764c44f52e1b93af1ef649f10c8f04ed38d))
* handle internal the websocket status based on GoodBye command ([1478bfd](https://github.com/gravitee-io/gravitee-exchange/commit/1478bfd7a183ec9e238a123caaeee1a1db376eac))
* improve and correct legacy adapters ([f97684f](https://github.com/gravitee-io/gravitee-exchange/commit/f97684fc3d9b6c92c57e0567d1543f9bac76187d))
* issue with batch configuration ([08cdf38](https://github.com/gravitee-io/gravitee-exchange/commit/08cdf384febef676b8555ead0167b50627c1d283))
* properly handle goodbye reconnection ([4f92d83](https://github.com/gravitee-io/gravitee-exchange/commit/4f92d835a09cd4fc117a140c997c7cd8c8a95881))
* properly handle serialization ([3321dcf](https://github.com/gravitee-io/gravitee-exchange/commit/3321dcfcdc33af9ad00e76cf784048f50aec3c69))
* properly manage controller shutdown process ([dd59ba2](https://github.com/gravitee-io/gravitee-exchange/commit/dd59ba206c6db22c004b4ae52c7de3504c85233d))
* properly set default primary value on websocket connector ([b6d7bb9](https://github.com/gravitee-io/gravitee-exchange/commit/b6d7bb9f41fd96e6360019e8f51347922ec077e4))
* properly use adapted cmd/reply when required ([7407a59](https://github.com/gravitee-io/gravitee-exchange/commit/7407a59d39f742d271befde216f6bb482e287e33))


### Features

* add channel metrics ([bf1d814](https://github.com/gravitee-io/gravitee-exchange/commit/bf1d81407187bdadc62d275c35732b7a026465e1))
* add key on a batch ([49ec37f](https://github.com/gravitee-io/gravitee-exchange/commit/49ec37f52e38c86c2d9b3d0e9216c8e27d869959))
* add observer to be notified when a batch finishes ([f958cb7](https://github.com/gravitee-io/gravitee-exchange/commit/f958cb7c5a512ff73e1b60ce79f37407beddac21))
* add protocol version on adapters factory ([8ac8d77](https://github.com/gravitee-io/gravitee-exchange/commit/8ac8d77f5a82779f35472c8cf567990949f3318d))
* allow to obtain connector status ([49f9d36](https://github.com/gravitee-io/gravitee-exchange/commit/49f9d3672ead035c893e7dd7a8e84ce0911670fb))
* improve controller metrics ([986079d](https://github.com/gravitee-io/gravitee-exchange/commit/986079d70a5454893266afe648d84c8f536fd37f))

# [1.0.0-alpha.4](https://github.com/gravitee-io/gravitee-exchange/compare/1.0.0-alpha.3...1.0.0-alpha.4) (2024-02-15)


### Bug Fixes

* apply correct version ([a3bac9e](https://github.com/gravitee-io/gravitee-exchange/commit/a3bac9e794e85eeccb3ab89a39bcf716823ec7d1))

# [1.0.0-alpha.3](https://github.com/gravitee-io/gravitee-exchange/compare/1.0.0-alpha.2...1.0.0-alpha.3) (2024-02-15)


### Features

* add command/reply adapters to manage command migration ([23b4605](https://github.com/gravitee-io/gravitee-exchange/commit/23b46050c8e63fc3453b00e079001c6a98ca1d04))

# [1.0.0-alpha.2](https://github.com/gravitee-io/gravitee-exchange/compare/1.0.0-alpha.1...1.0.0-alpha.2) (2024-02-07)


### Bug Fixes

* refactor serialization to avoid class cast ([42531a4](https://github.com/gravitee-io/gravitee-exchange/commit/42531a499d4628ab6e3ca398fdc08fe3bff94b66))

# 1.0.0-alpha.1 (2024-01-29)


### Features

* initialize command-reply exchange framework ([8cbac71](https://github.com/gravitee-io/gravitee-exchange/commit/8cbac71d14814c749a1c86d1a31e283da8450732))
