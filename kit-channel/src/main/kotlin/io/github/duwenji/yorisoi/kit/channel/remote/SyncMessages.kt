package io.github.duwenji.yorisoi.kit.channel.remote

import io.github.duwenji.yorisoi.core.text.localized

/** What [ChannelSync] tells the user, in the app's language (REQ-LANGUAGES F-4). Codes in brackets stay as they are. */
internal object SyncMessages {
    val badListId get() = localized(
        "リストIDの形が正しくありません（sc1 で始まる 35 文字）", "The list ID is not well formed (35 characters starting with sc1)",
        "列表 ID 的格式不正确（以 sc1 开头的 35 个字符）", "목록 ID 형식이 올바르지 않습니다(sc1로 시작하는 35자)",
    )
    val alreadyAdded get() = localized("このリストはもう登録されています", "This list is already added", "此列表已经添加过了", "이 목록은 이미 등록되어 있습니다")
    fun cantCheckList(detail: String?) = localized(
        "リストの情報を確かめられませんでした（$detail）", "Could not check the list ($detail)", "无法确认列表信息（$detail）", "목록 정보를 확인할 수 없었습니다($detail)",
    )
    fun cantReachList(detail: String?) = localized(
        "リストに接続できませんでした（$detail）", "Could not connect to the list ($detail)", "无法连接到列表（$detail）", "목록에 연결할 수 없었습니다($detail)",
    )
    val clockOff get() = localized(
        "端末の時計がずれているようです。時刻を確かめてください", "The device's clock seems to be off. Check the time",
        "设备的时钟似乎不准，请检查时间", "기기의 시계가 맞지 않는 것 같습니다. 시간을 확인해 주세요",
    )
    fun revoked(channel: String, list: String, reason: String) = localized(
        "「$channel」はリスト（$list）から取り下げられました: $reason", "“$channel” was withdrawn from the list ($list): $reason",
        "「$channel」已从列表（$list）中撤下：$reason", "「$channel」은(는) 목록($list)에서 내려졌습니다: $reason",
    )
    val listInvalid get() = localized(
        "リストが有効ではありません。取得し直してください", "The list is not valid. Fetch it again", "列表无效，请重新获取", "목록이 유효하지 않습니다. 다시 가져와 주세요",
    )
    val notInList get() = localized("リストにないチャンネルです", "This channel is not in the list", "列表中没有这个频道", "목록에 없는 채널입니다")
    val badFormat get() = localized(
        "このアプリでは使えない形式のチャンネルです", "This channel's format can't be used in this app", "这个频道的格式无法在本应用中使用", "이 앱에서는 사용할 수 없는 형식의 채널입니다",
    )
    val notForApp get() = localized(
        "このアプリでは使えないチャンネルです", "This channel can't be used in this app", "这个频道无法在本应用中使用", "이 앱에서는 사용할 수 없는 채널입니다",
    )
    val needsUpdate get() = localized(
        "このチャンネルを使うには、アプリの更新が必要です", "Update the app to use this channel", "要使用这个频道，需要更新应用", "이 채널을 사용하려면 앱을 업데이트해야 합니다",
    )
    val publisherChanged get() = localized(
        "チャンネル提供者が裏付けなく変わったため、新しい版は使いません（publisher_changed）",
        "The channel's publisher changed without proof, so the new version is not used (publisher_changed)",
        "频道提供者在没有证明的情况下发生了变更，因此不使用新版本（publisher_changed）",
        "채널 제공자가 근거 없이 바뀌어 새 버전은 사용하지 않습니다(publisher_changed)",
    )
    fun cantFetchPackage(detail: String?) = localized(
        "パッケージを取得できませんでした（$detail）", "Could not fetch the package ($detail)", "无法获取数据包（$detail）", "패키지를 가져올 수 없었습니다($detail)",
    )
    val packageNotList get() = localized(
        "パッケージがリストと一致しません（hash_mismatch）", "The package doesn't match the list (hash_mismatch)",
        "数据包与列表不一致（hash_mismatch）", "패키지가 목록과 일치하지 않습니다(hash_mismatch)",
    )
    val packageNotTicket get() = localized(
        "パッケージがチケットと一致しません（hash_mismatch）", "The package doesn't match the ticket (hash_mismatch)",
        "数据包与票据不一致（hash_mismatch）", "패키지가 티켓과 일치하지 않습니다(hash_mismatch)",
    )
    fun packageRejected(code: String) = localized(
        "パッケージの確認に失敗しました（package_rejected: $code）", "The package failed its check (package_rejected: $code)",
        "数据包检查失败（package_rejected: $code）", "패키지 확인에 실패했습니다(package_rejected: $code)",
    )
    fun keyMoved(channel: String, reason: String) = localized(
        "「$channel」のチャンネル提供者の鍵が移し替えられました: $reason", "The publisher of “$channel” moved to a new key: $reason",
        "「$channel」的频道提供者更换了密钥：$reason", "「$channel」 채널 제공자의 키가 바뀌었습니다: $reason",
    )
    val ticketNeedsDeveloperMode get() = localized(
        "試用チケットは開発者モードのときだけ読み込めます（ticket_invalid）", "Trial tickets can be loaded only in developer mode (ticket_invalid)",
        "试用票据只能在开发者模式下读取（ticket_invalid）", "체험 티켓은 개발자 모드에서만 불러올 수 있습니다(ticket_invalid)",
    )
    val notATicket get() = localized(
        "試用チケットとして読めません（ticket_invalid）", "This can't be read as a trial ticket (ticket_invalid)",
        "无法作为试用票据读取（ticket_invalid）", "체험 티켓으로 읽을 수 없습니다(ticket_invalid)",
    )
    fun addTicketListFirst(providerId: String) = localized(
        "この試用チケットのリスト（$providerId）を先に足してください", "Add this trial ticket's list ($providerId) first",
        "请先添加此试用票据的列表（$providerId）", "이 체험 티켓의 목록($providerId)을 먼저 추가해 주세요",
    )
    val noListYet get() = localized(
        "リストの情報がまだありません。「今すぐ確かめる」を押してから読み込んでください",
        "The list hasn't been fetched yet. Press “Check now”, then load the ticket",
        "还没有列表信息。请先按“立即检查”，再读取",
        "아직 목록 정보가 없습니다. “지금 확인”을 누른 뒤 불러와 주세요",
    )
    fun cantCheckTicket(detail: String?) = localized(
        "試用チケットを確かめられませんでした（$detail）", "Could not check the trial ticket ($detail)", "无法确认试用票据（$detail）", "체험 티켓을 확인할 수 없었습니다($detail)",
    )
    val ticketMismatch get() = localized(
        "パッケージがチケットのチャンネル・チャンネル提供者と違います（ticket_invalid）",
        "The package's channel or publisher is not the ticket's (ticket_invalid)",
        "数据包的频道或提供者与票据不符（ticket_invalid）",
        "패키지의 채널·채널 제공자가 티켓과 다릅니다(ticket_invalid)",
    )
    fun listExpired(list: String) = localized(
        "リスト（$list）の期限が切れたため、そのチャンネルは使えません。取得できるようになると戻ります",
        "The list ($list) has expired, so its channels can't be used. They come back once it can be fetched",
        "列表（$list）已过期，其中的频道暂时无法使用。能够获取后会恢复",
        "목록($list)의 기한이 지나 그 채널은 사용할 수 없습니다. 가져올 수 있게 되면 돌아옵니다",
    )
    val httpsOnly get() = localized(
        "URL は https:// で始まる必要があります", "The URL must start with https://", "URL 必须以 https:// 开头", "URL은 https://로 시작해야 합니다",
    )
}
