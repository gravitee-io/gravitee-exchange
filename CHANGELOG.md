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
