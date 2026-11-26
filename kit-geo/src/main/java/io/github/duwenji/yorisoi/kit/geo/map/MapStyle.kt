package io.github.duwenji.yorisoi.kit.geo.map

import io.github.duwenji.yorisoi.core.text.Words

/** Google's map types in the Map Tiles API (`mapType` of a session). */
enum class GoogleMapType(val apiName: String) {
    ROADMAP("roadmap"),
    SATELLITE("satellite"),
}

/**
 * The map under the spots. The GSI and OpenStreetMap maps need no key; the Google ones use the
 * user's own Map Tiles API key (Google Cloud, billed to them).
 */
enum class MapStyle(private val labels: Words, private val descriptions: Words, val google: GoogleMapType? = null) {
    GSI_STANDARD(
        Words("地理院 標準", "GSI Standard", "国土地理院 标准", "국토지리원 표준"),
        Words("国土地理院の標準地図", "The standard map of the Geospatial Information Authority of Japan", "日本国土地理院的标准地图", "일본 국토지리원의 표준 지도"),
    ),
    GSI_PALE(
        Words("地理院 淡色", "GSI Pale", "国土地理院 淡色", "국토지리원 연한 색"),
        Words(
            "色を抑えた地図。スポットやルートの線が目立ちます", "A map in soft colors, so spots and the route stand out",
            "颜色较淡的地图，地点和路线更醒目", "색을 줄인 지도. 장소와 경로가 잘 보입니다",
        ),
    ),
    GSI_PHOTO(
        Words("地理院 写真", "GSI Photo", "国土地理院 航拍", "국토지리원 사진"),
        Words("国土地理院の航空写真", "Aerial photos of the Geospatial Information Authority of Japan", "日本国土地理院的航拍照片", "일본 국토지리원의 항공 사진"),
    ),
    GSI_RELIEF(
        Words("地理院 標高", "GSI Elevation", "国土地理院 海拔", "국토지리원 표고"),
        Words(
            "高さを色で表した地図。坂や高低差がわかります（拡大は少し粗め）", "Heights in colors, to see slopes and rises (coarser when zoomed in)",
            "用颜色表示高度的地图，可以看出坡道和高低差（放大时稍粗）", "높이를 색으로 나타낸 지도. 언덕과 높낮이를 알 수 있습니다(확대하면 조금 거칠어집니다)",
        ),
    ),
    OSM_STANDARD(
        Words("OSM 標準", "OSM Standard", "OSM 标准", "OSM 표준"),
        Words(
            "OpenStreetMap の標準地図。取得できないときは地理院の標準地図で表示します",
            "The standard OpenStreetMap map. When it can't be fetched, the GSI standard map is shown",
            "OpenStreetMap 的标准地图。无法获取时显示国土地理院的标准地图",
            "OpenStreetMap의 표준 지도. 가져올 수 없을 때는 국토지리원 표준 지도로 표시합니다",
        ),
    ),
    GOOGLE_ROADMAP(
        Words("Google 地図", "Google Map", "Google 地图", "Google 지도"),
        Words(
            "Google マップの地図。Map Tiles API のキーが必要です", "The Google Maps map. Needs a Map Tiles API key",
            "Google 地图。需要 Map Tiles API 密钥", "Google 지도. Map Tiles API 키가 필요합니다",
        ),
        GoogleMapType.ROADMAP,
    ),
    GOOGLE_SATELLITE(
        Words("Google 航空写真", "Google Satellite", "Google 卫星图", "Google 위성 사진"),
        Words(
            "Google マップの航空写真。Map Tiles API のキーが必要です", "Google Maps aerial photos. Needs a Map Tiles API key",
            "Google 地图的卫星照片。需要 Map Tiles API 密钥", "Google 지도의 항공 사진. Map Tiles API 키가 필요합니다",
        ),
        GoogleMapType.SATELLITE,
    ),
    ;

    /** The name in the settings, in the app's language. */
    val label: String get() = labels.text

    /** What it shows, in the app's language. */
    val description: String get() = descriptions.text

    val needsGoogleKey: Boolean get() = google != null
}
