package net.daergoth.homewire.controlpanel;

import com.mongodb.client.MongoDatabase;
import net.daergoth.homewire.CustomMongoRepository;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Repository
public class LiveDataRepository extends CustomMongoRepository {

  private final Logger logger = LoggerFactory.getLogger(LiveDataRepository.class);

  private static final String COLLECTION_NAME = "live_data";

  @Autowired
  public LiveDataRepository(MongoDatabase db) {
    super(db);
  }

  @Override
  protected String getCollectionName() {
    return COLLECTION_NAME;
  }

  public void saveLiveData(LiveDataEntity liveDataEntity) {
    logger.info("Saving controlpanel data: {}", liveDataEntity);

    if (liveDataEntity.getValue() == null) {
      logger.warn("Live device data with null value!");

      return;
    }

    Document query = new Document()
        .append("type", liveDataEntity.getType());

    if (collection.count(query) > 0) {
      Document updated = new Document()
          .append("$set", new Document(
              "values." + String.valueOf(liveDataEntity.getId()), liveDataEntity.getValue()));

      collection.updateOne(query, updated);
    } else {
      Document document = new Document()
          .append("type", liveDataEntity.getType())
          .append("values", new Document()
              .append(String.valueOf(liveDataEntity.getId()), liveDataEntity.getValue())
          );
      collection.insertOne(document);
    }
  }

  public List<LiveDataEntity> getLiveData() {
    List<Document> docs = collection.find().into(new ArrayList<>());

    List<LiveDataEntity> result = new ArrayList<>();

    for (Document d : docs) {
      String type = d.getString("type");
      Document values = (Document) d.get("values");

      for (Map.Entry<String, Object> entry : values.entrySet()) {
        Short devId = Short.parseShort(entry.getKey());
        Double value = (Double) entry.getValue();

        result.add(new LiveDataEntity(devId, type, value.floatValue()));
      }
    }

    return result;
  }

  public LiveDataEntity getCurrentDeviceDataForIdAndType(Short deviceId, String deviceType) {
    Document match = new Document()
        .append("$match", new Document()
            .append("type", deviceType)
            .append("values." + deviceId,
                new Document("$exists", true)
            )
        );

    Document project = new Document()
        .append("$project", new Document()
            .append("_id", 0)
            .append("value", "$values." + deviceId)
        );

    Document valueDoc = collection.aggregate(Arrays.asList(match, project)).first();

    return new LiveDataEntity(deviceId, deviceType, valueDoc.getDouble("value").floatValue());
  }

  public void removeCurrentDeviceDataForDevIdAndDevType(Short devId, String devType) {
    Document filter = new Document()
        .append("type", devType);

    Document update = new Document()
        .append("$unset", new Document()
            .append("values." + devId, "")
        );

    collection.updateOne(filter, update);
  }
}
