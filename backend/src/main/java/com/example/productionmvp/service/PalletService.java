package com.example.productionmvp.service;

import com.example.productionmvp.exception.EntityNotFoundException;
import com.example.productionmvp.model.*;
import com.example.productionmvp.repository.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PalletService {

    private final PalletRepository palletRepository;
    private final ProductModelRepository productModelRepository;
    private final ProductInstanceRepository productInstanceRepository;
    private final AssemblyInstanceRepository assemblyInstanceRepository;
    private final PalletMovementRepository palletMovementRepository;
    private final PostRepository postRepository;
    private final WorkerRepository workerRepository;
    private final TaskRepository taskRepository;

    public PalletService(PalletRepository palletRepository,
                         ProductModelRepository productModelRepository,
                         ProductInstanceRepository productInstanceRepository,
                         AssemblyInstanceRepository assemblyInstanceRepository,
                         PalletMovementRepository palletMovementRepository,
                         PostRepository postRepository,
                         WorkerRepository workerRepository,
                         TaskRepository taskRepository) {
        this.palletRepository = palletRepository;
        this.productModelRepository = productModelRepository;
        this.productInstanceRepository = productInstanceRepository;
        this.assemblyInstanceRepository = assemblyInstanceRepository;
        this.palletMovementRepository = palletMovementRepository;
        this.postRepository = postRepository;
        this.workerRepository = workerRepository;
        this.taskRepository = taskRepository;
    }

    // ТЗ §2.7: a pallet is physically collected for one specific product instance
    // (e.g. "П-04-РИЧАГИ" for Мауль #04), not just "some product of this model".
    @Transactional
    public Pallet createPallet(UUID productId, UUID productInstanceId, String category) {
        ProductModel product = null;
        if (productId != null) {
            product = productModelRepository.findById(productId).orElse(null);
        }

        ProductInstance ownerProduct = null;
        if (productInstanceId != null) {
            ownerProduct = productInstanceRepository.findById(productInstanceId)
                    .orElseThrow(() -> new EntityNotFoundException("ProductInstance not found: " + productInstanceId));
            if (product == null) {
                product = ownerProduct.getProductModel();
            }
        }

        String prefix = product != null ? product.getId().toString().substring(0, 4) : "GEN";
        String qrCode = String.format("P-%s-%s-%d", prefix, category, System.currentTimeMillis());

        Pallet pallet = new Pallet();
        pallet.setQrCode(qrCode);
        pallet.setCategory(category);
        pallet.setOwnerProduct(ownerProduct);
        pallet.setStatus(PalletStatus.EMPTY);
        return palletRepository.save(pallet);
    }

    @Transactional
    public Pallet addAssemblyToPallet(UUID palletId, UUID assemblyInstanceId) {
        Pallet pallet = palletRepository.findById(palletId)
                .orElseThrow(() -> new EntityNotFoundException("Pallet not found"));
        AssemblyInstance instance = assemblyInstanceRepository.findById(assemblyInstanceId)
                .orElseThrow(() -> new EntityNotFoundException("AssemblyInstance not found"));

        // instance.setPallet(pallet); // Assuming relation
        pallet.getAssemblyInstances().add(instance);
        palletRepository.save(pallet);
        
        return pallet;
    }

    @Transactional
    public PalletMovement movePallet(UUID palletId, UUID toPostId, UUID movedByWorkerId) {
        Pallet pallet = palletRepository.findById(palletId)
                .orElseThrow(() -> new EntityNotFoundException("Pallet not found"));
        Post toPost = postRepository.findById(toPostId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found"));
        Worker worker = workerRepository.findById(movedByWorkerId)
                .orElseThrow(() -> new EntityNotFoundException("Worker not found"));

        pallet.setCurrentPost(toPost);
        palletRepository.save(pallet);

        PalletMovement movement = new PalletMovement();
        movement.setPallet(pallet);
        movement.setToPost(toPost);
        movement.setMovedBy(worker);
        movement.setMovedAt(LocalDateTime.now());
        
        return palletMovementRepository.save(movement);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPalletByQr(String qrCode) {
        Pallet pallet = palletRepository.findByQrCode(qrCode)
                .orElseThrow(() -> new EntityNotFoundException("Pallet not found for QR: " + qrCode));

        Map<String, Object> data = new HashMap<>();
        data.put("pallet", pallet);
        data.put("currentPost", pallet.getCurrentPost());
        data.put("assemblies", pallet.getAssemblyInstances());

        // ТЗ §7: after scanning a pallet the operator should see which operation is
        // actionable for the post the pallet is currently sitting at. We look at the
        // already-created tasks for each assembly instance on the pallet (created when
        // the product was launched / the previous operation completed) and surface the
        // ones that are actionable and, if the pallet has a known post, match that post.
        List<com.example.productionmvp.dto.TaskDTO> availableOperations = new ArrayList<>();
        if (pallet.getAssemblyInstances() != null) {
            for (AssemblyInstance instance : pallet.getAssemblyInstances()) {
                for (Task task : taskRepository.findByAssemblyInstanceId(instance.getId())) {
                    boolean actionable = task.getStatus() == TaskStatus.READY
                            || task.getStatus() == TaskStatus.CREATED
                            || task.getStatus() == TaskStatus.ASSIGNED;
                    boolean postMatches = pallet.getCurrentPost() == null
                            || task.getPost() == null
                            || pallet.getCurrentPost().getId().equals(task.getPost().getId());
                    if (actionable && postMatches) {
                        availableOperations.add(new com.example.productionmvp.dto.TaskDTO(task));
                    }
                }
            }
        }
        data.put("availableOperations", availableOperations);

        return data;
    }

    public byte[] generateQrCodeImage(String qrCode) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(qrCode, BarcodeFormat.QR_CODE, 200, 200);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generating QR code", e);
        }
    }
}
