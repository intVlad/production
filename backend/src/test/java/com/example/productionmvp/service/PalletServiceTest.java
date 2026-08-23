package com.example.productionmvp.service;

import com.example.productionmvp.exception.EntityNotFoundException;
import com.example.productionmvp.model.*;
import com.example.productionmvp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PalletServiceTest {

    @Mock
    private PalletRepository palletRepository;
    @Mock
    private ProductModelRepository productModelRepository;
    @Mock
    private AssemblyInstanceRepository assemblyInstanceRepository;
    @Mock
    private PalletMovementRepository palletMovementRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private WorkerRepository workerRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private ProductInstanceRepository productInstanceRepository;

    @InjectMocks
    private PalletService palletService;

    private UUID palletId;
    private UUID productId;
    private UUID assemblyInstanceId;
    private UUID postId;
    private UUID workerId;

    private Pallet pallet;
    private ProductModel product;
    private AssemblyInstance assemblyInstance;
    private Post post;
    private Worker worker;

    @BeforeEach
    void setUp() {
        palletId = UUID.randomUUID();
        productId = UUID.randomUUID();
        assemblyInstanceId = UUID.randomUUID();
        postId = UUID.randomUUID();
        workerId = UUID.randomUUID();

        pallet = new Pallet();
        pallet.setId(palletId);
        pallet.setQrCode("TEST-QR");
        pallet.setAssemblyInstances(new ArrayList<>());

        product = new ProductModel();
        product.setId(productId);

        assemblyInstance = new AssemblyInstance();
        assemblyInstance.setId(assemblyInstanceId);

        post = new Post();
        post.setId(postId);

        worker = new Worker();
        worker.setId(workerId);
    }

    @Test
    void testCreatePallet_ProductIdNull() {
        when(palletRepository.save(any(Pallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Pallet created = palletService.createPallet(null, null, "CAT1");

        assertNotNull(created);
        assertEquals("CAT1", created.getCategory());
        assertEquals(PalletStatus.EMPTY, created.getStatus());
        assertTrue(created.getQrCode().startsWith("P-GEN-CAT1-"));
        verify(productModelRepository, never()).findById(any());
        verify(palletRepository).save(any(Pallet.class));
    }

    @Test
    void testCreatePallet_ProductFound() {
        when(productModelRepository.findById(productId)).thenReturn(Optional.of(product));
        when(palletRepository.save(any(Pallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Pallet created = palletService.createPallet(productId, null, "CAT2");

        assertNotNull(created);
        assertEquals("CAT2", created.getCategory());
        assertEquals(PalletStatus.EMPTY, created.getStatus());
        
        String expectedPrefix = productId.toString().substring(0, 4);
        assertTrue(created.getQrCode().startsWith("P-" + expectedPrefix + "-CAT2-"));
        verify(productModelRepository).findById(productId);
        verify(palletRepository).save(any(Pallet.class));
    }

    @Test
    void testCreatePallet_ProductNotFound() {
        when(productModelRepository.findById(productId)).thenReturn(Optional.empty());
        when(palletRepository.save(any(Pallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Pallet created = palletService.createPallet(productId, null, "CAT3");

        assertNotNull(created);
        assertEquals("CAT3", created.getCategory());
        assertEquals(PalletStatus.EMPTY, created.getStatus());
        assertTrue(created.getQrCode().startsWith("P-GEN-CAT3-"));
        verify(productModelRepository).findById(productId);
        verify(palletRepository).save(any(Pallet.class));
    }

    @Test
    void testCreatePallet_WithProductInstance_SetsOwnerProduct() {
        UUID productInstanceId = UUID.randomUUID();
        ProductInstance instance = new ProductInstance();
        instance.setId(productInstanceId);
        instance.setProductModel(product);

        when(productInstanceRepository.findById(productInstanceId)).thenReturn(Optional.of(instance));
        when(palletRepository.save(any(Pallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Pallet created = palletService.createPallet(null, productInstanceId, "CAT4");

        assertNotNull(created);
        assertEquals(instance, created.getOwnerProduct());
        String expectedPrefix = productId.toString().substring(0, 4);
        assertTrue(created.getQrCode().startsWith("P-" + expectedPrefix + "-CAT4-"));
    }

    @Test
    void testCreatePallet_ProductInstanceNotFound_Throws() {
        UUID productInstanceId = UUID.randomUUID();
        when(productInstanceRepository.findById(productInstanceId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> palletService.createPallet(null, productInstanceId, "CAT5"));
        verify(palletRepository, never()).save(any());
    }

    @Test
    void testAddAssemblyToPallet_Success() {
        when(palletRepository.findById(palletId)).thenReturn(Optional.of(pallet));
        when(assemblyInstanceRepository.findById(assemblyInstanceId)).thenReturn(Optional.of(assemblyInstance));
        when(palletRepository.save(any(Pallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Pallet updatedPallet = palletService.addAssemblyToPallet(palletId, assemblyInstanceId);

        assertNotNull(updatedPallet);
        assertTrue(updatedPallet.getAssemblyInstances().contains(assemblyInstance));
        verify(palletRepository).save(pallet);
    }

    @Test
    void testAddAssemblyToPallet_PalletNotFound() {
        when(palletRepository.findById(palletId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> palletService.addAssemblyToPallet(palletId, assemblyInstanceId));
        
        verify(assemblyInstanceRepository, never()).findById(any());
        verify(palletRepository, never()).save(any());
    }

    @Test
    void testAddAssemblyToPallet_AssemblyNotFound() {
        when(palletRepository.findById(palletId)).thenReturn(Optional.of(pallet));
        when(assemblyInstanceRepository.findById(assemblyInstanceId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> palletService.addAssemblyToPallet(palletId, assemblyInstanceId));
        
        verify(palletRepository, never()).save(any());
    }

    @Test
    void testMovePallet_Success() {
        when(palletRepository.findById(palletId)).thenReturn(Optional.of(pallet));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(palletMovementRepository.save(any(PalletMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PalletMovement movement = palletService.movePallet(palletId, postId, workerId);

        assertNotNull(movement);
        assertEquals(pallet, movement.getPallet());
        assertEquals(post, movement.getToPost());
        assertEquals(worker, movement.getMovedBy());
        assertNotNull(movement.getMovedAt());
        
        assertEquals(post, pallet.getCurrentPost());
        verify(palletRepository).save(pallet);
        verify(palletMovementRepository).save(any(PalletMovement.class));
    }

    @Test
    void testMovePallet_PalletNotFound() {
        when(palletRepository.findById(palletId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> palletService.movePallet(palletId, postId, workerId));
        
        verify(postRepository, never()).findById(any());
        verify(workerRepository, never()).findById(any());
        verify(palletRepository, never()).save(any());
        verify(palletMovementRepository, never()).save(any());
    }

    @Test
    void testMovePallet_PostNotFound() {
        when(palletRepository.findById(palletId)).thenReturn(Optional.of(pallet));
        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> palletService.movePallet(palletId, postId, workerId));
        
        verify(workerRepository, never()).findById(any());
        verify(palletRepository, never()).save(any());
        verify(palletMovementRepository, never()).save(any());
    }

    @Test
    void testMovePallet_WorkerNotFound() {
        when(palletRepository.findById(palletId)).thenReturn(Optional.of(pallet));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(workerRepository.findById(workerId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> palletService.movePallet(palletId, postId, workerId));
        
        verify(palletRepository, never()).save(any());
        verify(palletMovementRepository, never()).save(any());
    }

    @Test
    void testGetPalletByQr_Success() {
        pallet.setCurrentPost(post);
        when(palletRepository.findByQrCode("TEST-QR")).thenReturn(Optional.of(pallet));

        Map<String, Object> result = palletService.getPalletByQr("TEST-QR");

        assertNotNull(result);
        assertEquals(pallet, result.get("pallet"));
        assertEquals(post, result.get("currentPost"));
        assertEquals(pallet.getAssemblyInstances(), result.get("assemblies"));
        assertEquals(Collections.emptyList(), result.get("availableOperations"));
    }

    @Test
    void testGetPalletByQr_ReturnsActionableTaskForCurrentPost() {
        pallet.setCurrentPost(post);
        pallet.getAssemblyInstances().add(assemblyInstance);
        when(palletRepository.findByQrCode("TEST-QR")).thenReturn(Optional.of(pallet));

        Task actionable = new Task();
        actionable.setId(UUID.randomUUID());
        actionable.setStatus(TaskStatus.READY);
        actionable.setPost(post);

        Task wrongPost = new Task();
        wrongPost.setStatus(TaskStatus.READY);
        Post otherPost = new Post();
        otherPost.setId(UUID.randomUUID());
        wrongPost.setPost(otherPost);

        Task notActionable = new Task();
        notActionable.setStatus(TaskStatus.COMPLETED);
        notActionable.setPost(post);

        when(taskRepository.findByAssemblyInstanceId(assemblyInstanceId))
                .thenReturn(List.of(actionable, wrongPost, notActionable));

        Map<String, Object> result = palletService.getPalletByQr("TEST-QR");

        @SuppressWarnings("unchecked")
        List<com.example.productionmvp.dto.TaskDTO> available =
                (List<com.example.productionmvp.dto.TaskDTO>) result.get("availableOperations");
        assertEquals(1, available.size());
        assertEquals(actionable.getId(), available.get(0).getId());
    }

    @Test
    void testGetPalletByQr_NotFound() {
        when(palletRepository.findByQrCode("INVALID-QR")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> palletService.getPalletByQr("INVALID-QR"));
    }

    @Test
    void testGenerateQrCodeImage_Success() {
        byte[] imageBytes = palletService.generateQrCodeImage("TEST-QR-123");

        assertNotNull(imageBytes);
        assertTrue(imageBytes.length > 0);
    }
    
    @Test
    void testGenerateQrCodeImage_NullQrCode() {
        assertThrows(RuntimeException.class, () -> palletService.generateQrCodeImage(null));
    }
}
