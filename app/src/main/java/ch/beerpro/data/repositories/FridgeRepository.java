package ch.beerpro.data.repositories;

import android.util.Log;
import android.util.LogPrinter;
import android.util.Pair;
import android.widget.Toast;

import androidx.lifecycle.LiveData;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import ch.beerpro.domain.models.Beer;
import ch.beerpro.domain.models.Entity;
import ch.beerpro.domain.models.FridgeEntry;
import ch.beerpro.domain.models.Wish;
import ch.beerpro.domain.utils.FirestoreQueryLiveData;
import ch.beerpro.domain.utils.FirestoreQueryLiveDataArray;
import ch.beerpro.presentation.details.DetailsActivity;

import static androidx.lifecycle.Transformations.map;
import static androidx.lifecycle.Transformations.switchMap;
import static ch.beerpro.domain.utils.LiveDataExtensions.combineLatest;
import static com.firebase.ui.auth.AuthUI.getApplicationContext;

public class FridgeRepository {


    private static LiveData<List<FridgeEntry>> getFridgeEntriesByUser(String userId) {
        return new FirestoreQueryLiveDataArray<>(FirebaseFirestore.getInstance().collection(FridgeEntry.COLLECTION)
                .orderBy(FridgeEntry.FIELD_ADDED_AT, Query.Direction.DESCENDING).whereEqualTo(FridgeEntry.FIELD_USER_ID, userId),
                FridgeEntry.class);
    }

    private static LiveData<FridgeEntry> getUserFridgeListFor(Pair<String, Beer> input) {
        String userId = input.first;
        Beer beer = input.second;
        DocumentReference document = FirebaseFirestore.getInstance().collection(FridgeEntry.COLLECTION)
                .document(Wish.generateId(userId, beer.getId()));
        return new FirestoreQueryLiveData<>(document, FridgeEntry.class);
    }

    public Task<Void> addUserFridgeItem(String userId, String itemId, int amount) {

        // add amount of items to fridge
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String fridgeEntryId = FridgeEntry.generateId(userId, itemId);
        DocumentReference fridgeEntryQuery = db.collection(FridgeEntry.COLLECTION).document(fridgeEntryId);
        return fridgeEntryQuery.get().continueWithTask(task ->{
            if (task.isSuccessful() && task.getResult().exists()) {
                long current = ((long) task.getResult().getData().get("amount"));
                if(current >= Integer.MAX_VALUE)
                    throw task.getException();
                current += amount;
                return fridgeEntryQuery.set(new FridgeEntry(userId, itemId, (int)current, new Date()));
            } else if (task.isSuccessful()) {
                return fridgeEntryQuery.set(new FridgeEntry(userId, itemId, amount, new Date()));
            } else {
                throw task.getException();
            }
        });
    }

    public Task<Void> drinkBeerFromFridge(String userId, String itemId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String fridgeEntryId = FridgeEntry.generateId(userId, itemId);
        DocumentReference fridgeEntryQuery = db.collection(FridgeEntry.COLLECTION).document(fridgeEntryId);
        return fridgeEntryQuery.get().continueWithTask(task ->{
            if (task.isSuccessful() && task.getResult().exists()) {
                long current = ((long) task.getResult().getData().get("amount"));
                if(current < 0)
                    throw task.getException();
                if(current < 2){
                    // remove item from list
                    return fridgeEntryQuery.delete();
                }
                current --;
                return fridgeEntryQuery.set(new FridgeEntry(userId, itemId, (int)current, new Date()));
            }  else {
                throw task.getException();
            }
        });
    }

    public LiveData<List<Pair<FridgeEntry, Beer>>> getMyFridgeWithBeers(LiveData<String> currentUserId, LiveData<List<Beer>> allBeers) {
        return map(combineLatest(getMyFridgelist(currentUserId), map(allBeers, Entity::entitiesById)), input -> {
            List<FridgeEntry> entries = input.first;
            HashMap<String, Beer> beersById = input.second;

            ArrayList<Pair<FridgeEntry, Beer>> result = new ArrayList<>();
            for (FridgeEntry entry : entries) {
                Beer beer = beersById.get(entry.getBeerId());
                result.add(Pair.create(entry, beer));
            }
            return result;
        });
    }

    public LiveData<List<FridgeEntry>> getMyFridgelist(LiveData<String> currentUserId) {
        return switchMap(currentUserId, FridgeRepository::getFridgeEntriesByUser);
    }


    public LiveData<FridgeEntry> getMyFridgeEntry(LiveData<String> currentUserId, LiveData<Beer> beer) {


        return switchMap(combineLatest(currentUserId, beer), FridgeRepository::getUserFridgeListFor);
    }


}
