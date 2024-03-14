import com.codacy.plugin.services.api.Api
import com.codacy.plugin.services.api.models.ToolDetails

object Tools {
    private var all: List<ToolDetails>? = null

    suspend fun init() {
//        TODO: fix
//        Api().listTools { response ->
//            all = response.data
//        }
    }

    fun getTool(uuid: String): ToolDetails? {
        return all?.find { it.uuid == uuid }
    }
}
